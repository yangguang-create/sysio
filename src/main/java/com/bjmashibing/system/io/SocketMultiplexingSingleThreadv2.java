package com.bjmashibing.system.io;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * 多线程的模型，把回调方法扔到别的线程，用其他的cpu进行处理。
 * 当前线程只负责接收连接，发现IO里面是要读，要写，的时候，扔给别的线程处理。
 *  充分利用，多线程，单多路复用器，把读写业务扔出去，交给其他线程处理，多路复用器还是一个。
 *  所有的事件，所有的读写，都是要扔到另外一个线程中去做，主线程就是负责客户端的连接。
 *
 *  为了在主线程中使用一个多路复用器，用其它的线程处理业务逻辑，这时会产生事件重复调用
 *  为了避免writeHandler、acceptHandler。readHandler方法的重复调用，
 *  我们在我们频繁的从selector中register和cancel，保证了有这样一个交替。才能避免重复调用。
 *  所以：要解决这个问题，就是既要用到多核，还不要频繁的注册，取消(也就是频繁的epoll_ctl()的调用)，
 *
 */
public class SocketMultiplexingSingleThreadv2 {

    private ServerSocketChannel server = null;
    private Selector selector = null;   //linux 多路复用器（select poll epoll） nginx  event{}
    int port = 9090;

    public void initServer() {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.bind(new InetSocketAddress(port));
            selector = Selector.open();  //  select  poll  *epoll
            server.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        initServer();
        System.out.println("服务器启动了。。。。。");
        try {
            while (true) {
//                Set<SelectionKey> keys = selector.keys();
//                System.out.println(keys.size()+"   size");
                while (selector.select(50) > 0) {
                    Set<SelectionKey> selectionKeys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = selectionKeys.iterator();
                    while (iter.hasNext()) {
                        SelectionKey key = iter.next();
                        iter.remove();//把返回事件的结果集remove掉了，并不是不内核空间的fd给remove掉。
                        if (key.isAcceptable()) {
                            acceptHandler(key);
                        } else if (key.isReadable()) {
                            key.cancel();  //现在多路复用器里把key  cancel了：所以，就是在时差里，重复循环时select中就没有这个key了。所以readHandler就不会重复读了
                            System.out.println("in.....");
                            key.interestOps(key.interestOps() | ~SelectionKey.OP_READ);

                            readHandler(key);//还是阻塞的嘛？ 即便以抛出了线程去读取，但是在时差里，由于key取消，所以还是会重复调用这个readHandler，这个key的read事件会被重复触发

                        } else if(key.isWritable()){  //我之前没讲过写的事件！！！！！
                            //写事件什么时候会发生，写事件和 send-queue有关，  只要是send-queue是空的，就一定会给你返回可以写的事件，就会回调我们的写方法
                            //意味着，我们的回调方法writeHandler()会被一直调用.
                            //你真的要明白：什么时候写？不是依赖send-queue是不是有空间,而是取决于自己要不要写.
                            //1，你准备好要写什么了，这是第一步
                            //2，第二步你才关心send-queue是否有空间
                            //3，so，读 read 一开始就要注册，但是write依赖以上关系，只有读到了，才可以写给客户端，什么时候用什么时候注册
                            //4，如果一开始就注册了write的事件，进入死循环，一直调起(这个调用没有意义)！！！
                            //所以：应该从多路复用器中把key扔掉，然后再去写，不让写事件由于send-queue来触发.
                            key.cancel();
                            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);


                            writeHandler(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeHandler(SelectionKey key) {
        new Thread(()->{
            System.out.println("write handler...");
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            buffer.flip();
            while (buffer.hasRemaining()) {
                try {

                    client.write(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            buffer.clear();
//            key.cancel();

//            try {
////                client.shutdownOutput();
//
////                client.close();
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
        }).start();

    }

    public void acceptHandler(SelectionKey key) {
        try {
            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
            SocketChannel client = ssc.accept();
            client.configureBlocking(false);
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            client.register(selector, SelectionKey.OP_READ, buffer);
            System.out.println("-------------------------------------------");
            System.out.println("新客户端：" + client.getRemoteAddress());
            System.out.println("-------------------------------------------");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void readHandler(SelectionKey key) {
        new Thread(()->{
            System.out.println("read handler.....");
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = (ByteBuffer) key.attachment();
            buffer.clear();
            int read = 0;
            try {
                while (true) {
                    read = client.read(buffer);
                    System.out.println(Thread.currentThread().getName()+ " " + read);
                    if (read > 0) {
                        key.interestOps(  SelectionKey.OP_READ);

                        client.register(key.selector(),SelectionKey.OP_WRITE,buffer);
                    } else if (read == 0) {
                        break;
                    } else {
                        client.close();
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

    }

    public static void main(String[] args) {
        SocketMultiplexingSingleThreadv2 service = new SocketMultiplexingSingleThreadv2();
        service.start();
    }
}
