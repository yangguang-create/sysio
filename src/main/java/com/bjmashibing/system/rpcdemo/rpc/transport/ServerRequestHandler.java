package com.bjmashibing.system.rpcdemo.rpc.transport;

import com.bjmashibing.system.rpcdemo.util.Packmsg;
import com.bjmashibing.system.rpcdemo.util.SerDerUtil;
import com.bjmashibing.system.rpcdemo.rpc.Dispatcher;
import com.bjmashibing.system.rpcdemo.rpc.protocol.MyContent;
import com.bjmashibing.system.rpcdemo.rpc.protocol.Myheader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author: 马士兵教育
 * @create: 2020-08-16 21:13
 */
public class ServerRequestHandler extends ChannelInboundHandlerAdapter {

    Dispatcher dis;

    public ServerRequestHandler(Dispatcher dis) {
        this.dis=dis;
    }

    //provider:
    //思考下解决方法？
    //
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        Packmsg requestPkg = (Packmsg) msg;

//        System.out.println("server handler :"+ requestPkg.content.getArgs()[0]);

        //如果假设处理完了，要给客户端返回了~！！！
        //你需要注意哪些环节~！！！！！！！！

        //bytebuf
        //因为是个RPC吗，你得有requestID！！！！
        //在client那一侧也要解决解码问题

        //关注rpc通信协议  来的时候flag 0x14141414

        //有新的header+content
        String ioThreadName = Thread.currentThread().getName();
        //1,直接在当前方法 处理IO和业务和返回

        //3，自己创建线程池
        //2,使用netty自己的eventloop来处理业务及返回
        ctx.executor().execute(new Runnable() {
//        ctx.executor().parent().next().execute(new Runnable() {

            @Override
            public void run() {

                String serviceName = requestPkg.getContent().getName();
                String method = requestPkg.getContent().getMethodName();
                Object c = dis.get(serviceName);
                Class<?> clazz = c.getClass();
                Object res = null;
                try {


                    Method m = clazz.getMethod(method, requestPkg.getContent().getParameterTypes());
                    res = m.invoke(c, requestPkg.getContent().getArgs());


                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }


//                String execThreadName = Thread.currentThread().getName();
                MyContent content = new MyContent();
//                String s = "io thread: " + ioThreadName + " exec thread: " + execThreadName + " from args:" + requestPkg.content.getArgs()[0];
                content.setRes(res);
                byte[] contentByte = SerDerUtil.ser(content);

                Myheader resHeader = new Myheader();
                resHeader.setRequestID(requestPkg.getHeader().getRequestID());
                resHeader.setFlag(0x14141424);
                resHeader.setDataLen(contentByte.length);
                byte[] headerByte = SerDerUtil.ser(resHeader);
                ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.directBuffer(headerByte.length + contentByte.length);

                byteBuf.writeBytes(headerByte);
                byteBuf.writeBytes(contentByte);
                ctx.writeAndFlush(byteBuf);
            }
        });

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println("client close");
    }
}

