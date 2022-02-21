package com.bjmashibing.system.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/*
*   单线程中，有接收有读取。
* */
public class SocketMultiplexingSingleThreadv1 {

    //马老师的坦克 一 二期
    private ServerSocketChannel server = null;
    private Selector selector = null;   //linux 多路复用器（select poll    epoll kqueue） nginx  event{}
    int port = 9090;

    public void initServer() {
        try {
            server = ServerSocketChannel.open();//初始化server
            server.configureBlocking(false);//设置成非阻塞
            server.bind(new InetSocketAddress(port));//绑定端口号


            //如果在epoll模型下，open相当于就完成了epoll_create -> fd3(开辟了一个空间)
            //select和poll模型下，在java底层c代码上开辟空间，和内核没有交互。
            selector = Selector.open();  //  select  poll  *epoll  linux中优先选择：epoll  但是在程序启动的时候可以使用-D修正，来指定选择哪种模型来使用。

            //server 约等于 listen状态的 fd4
            /*
                register：java中的一套API可以对底层的多种模型都支持。
                如果：
                select，poll：jvm里开辟一个数组 fd4 放进去
                epoll：是在内核中开辟一个红黑树的数据结构fd3，通过epoll_ctl(fd3,ADD,fd4,EPOLLIN)，把监听状态的fd4放到fd3中。
             */
            server.register(selector, SelectionKey.OP_ACCEPT);


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        initServer();
        System.out.println("服务器启动了。。。。。");
        try {
            while (true) {  //死循环

                Set<SelectionKey> keys = selector.keys();
                System.out.println(keys.size()+"   size");


                //1,调用多路复用器(select,poll  or  epoll (epoll模型时其实是调用epoll_wait))
                /*
                select()是啥意思：根据IO模型不同含义不一样
                1，select，poll  其实  内核的select（fd4）  poll(fd4)
                2，epoll：  其实 内核的 epoll_wait()
                *, 参数可以带时间：没有时间，0  ：  阻塞，有时间设置一个超时
                selector.wakeup()  结果返回0：外部线程可以调用这个方法，取消当前线程的阻塞.

                类似于懒加载：
                其实再触碰到selector.select()调用的时候触发了epoll_ctl的调用，epoll_ctl()会被延迟调用。
                epoll_ctl不会在注册监听socket的Fd4时调用.
                epoll_ctl一定会在epoll_wait之前添加到内存结构中.
                 */
                while (selector.select() > 0) {
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();  //返回的有状态的fd集合
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    //so，管你啥多路复用器，你呀只能给我状态，我还得一个一个的去处理他们的R/W。同步好辛苦！！！！！！！！
                    //  NIO  自己对着每一个fd调用系统调用，浪费资源，那么你看，这里是不是调用了一次select方法，知道具体的那些可以R/W了？
                    //幕兰，是不是很省力？
                    //我前边可以强调过，socket分为两种：  listen转态的socket、通信R/W状态的socket.
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove(); //set  不移除会重复循环处理
                        if (key.isAcceptable()) {
                            //看代码的时候，这里是重点，如果要去接受一个新的连接
                            //语义上，accept接受连接且返回新连接的FD对吧？
                            //那新的FD怎么办？
                            //select，poll，因为他们内核没有空间，那么在jvm中保存和前边的fd4那个listen的一起
                            //epoll： 我们希望通过epoll_ctl把新的客户端fd注册到内核空间
                            acceptHandler(key);
                        } else if (key.isReadable()) {
                            readHandler(key);  //连read 还有 write都处理了
                            //在当前线程，这个方法可能会阻塞  ，如果阻塞了十年，其他的IO早就没电了。。。
                            //所以，为什么提出了 IO THREADS
                            //redis  是不是用了epoll，redis是不是有个io threads的概念 ，redis是不是单线程的
                            //tomcat 8,9  异步的处理方式  IO  和   处理上  解耦
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void acceptHandler(SelectionKey key) {
        try {
            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
            SocketChannel client = ssc.accept(); //来啦，目的是调用accept接受客户端  fd7
            client.configureBlocking(false);

            ByteBuffer buffer = ByteBuffer.allocate(8192);  //前边讲过了

            // 0.0  我类个去
            //你看，调用了register，把新的客户端FD注册到内核空间上。
            /*
            select，poll：jvm里开辟一个数组 fd7 放进去
            epoll：  epoll_ctl(fd3,ADD,fd7,EPOLLIN
             */
            client.register(selector, SelectionKey.OP_READ, buffer);
            System.out.println("-------------------------------------------");
            System.out.println("新客户端：" + client.getRemoteAddress());
            System.out.println("-------------------------------------------");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 这个方法中，连读带写都处理了。
     * @param key
     */
    public void readHandler(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        buffer.clear();
        int read = 0;
        try {
            while (true) {
                read = client.read(buffer);//基于事件POLLIN，有事才读，没事不瞎看。。。读的处理
                if (read > 0) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        //此时就可以利用这个FD7读客户端发送过来的数据了。读完之后，再写.
                        client.write(buffer);
                    }
                    buffer.clear();
                } else if (read == 0) {
                    break;
                } else {//-1:对面把程序结束的时候，这时读的是-1，这时可以把服务器给close即可。如果不close，就会出现CLOSE_WAIT状态和FIN_WAIT状态。
                    client.close();
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();

        }
    }

    public static void main(String[] args) {
        SocketMultiplexingSingleThreadv1 service = new SocketMultiplexingSingleThreadv1();
        service.start();
    }
}
