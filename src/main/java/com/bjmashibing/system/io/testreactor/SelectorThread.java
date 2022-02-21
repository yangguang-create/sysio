package com.bjmashibing.system.io.testreactor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author: 马士兵教育
 * @create: 2020-06-21 20:14
 * 一定要保证多线程情况下的代码执行的先后顺序问题.
 *      这就涉及了多线程情况下的通信问题，一般是通过队列的方式来实现多线程的同步问题。
 *      因为队列是堆里面的对象，多线程情况下，堆里面的内容是共享的，栈是线程堆里的空间，所以，可以使用堆里面的对象调节线程之间的先后顺序的执行问题.
 */
public class SelectorThread  extends  ThreadLocal<LinkedBlockingQueue<Channel>>  implements   Runnable{
    // 每线程对应一个selector，
    // 多线程情况下，该主机，该程序的并发客户端被分配到多个selector上
    //注意，每个客户端，只绑定到其中一个selector
    //每个selector只负责一部分FD，它们之间其实不会有交互问题



    Selector  selector = null;
    //使用队列的方式实现和主线程之间的同步.
//    LinkedBlockingQueue<Channel> lbq = new LinkedBlockingQueue<>();
    LinkedBlockingQueue<Channel> lbq = get();  //lbq  在接口或者类中是固定使用方式逻辑写死了。你需要是lbq每个线程持有自己的独立对象

    //持有SelectorThreadGroup的引用，然后为客户端的连接做注册
    SelectorThreadGroup stg;

    @Override
    protected LinkedBlockingQueue<Channel> initialValue() {
        return new LinkedBlockingQueue<>();//你要丰富的是这里！  pool。。。
    }

    SelectorThread(SelectorThreadGroup stg){
        try {
            this.stg = stg;//构造的时候初始化.
            selector = Selector.open();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {

        //Loop
        while (true){
            try {
                //1,select()：不设置超时时间的话，会一直阻塞。阻塞的时候，其他线程可以调用wakeup()方法来唤醒当前阻塞，让代码继续执行
//                System.out.println(Thread.currentThread().getName()+"   :  before select...."+ selector.keys().size());
                int nums = selector.select();  //阻塞  wakeup()
//                Thread.sleep(1000);  //这绝对不是解决方案，我只是给你演示:
//                System.out.println(Thread.currentThread().getName()+"   :  after select...." + selector.keys().size());

                //2,处理selectkeys：主线程调用wakeup方法后，第二步肯定会由于num为0，所以会直接跳过，执行第三步。
                if(nums>0){
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> iter = keys.iterator();
                    while(iter.hasNext()){  //每一个selector中都是线性处理，线程处理的过程
                        SelectionKey key = iter.next();
                        iter.remove();
                        if(key.isAcceptable()){  //复杂,接受客户端的过程（接收之后，要注册，但是在多线程下，新的客户端，注册到哪个selector里呢？）
                            acceptHandler(key);
                        }else if(key.isReadable()){
                            readHander(key);
                        }else if(key.isWritable()){

                        }



                    }

                }
                //3,处理一些task : task有可能是listen和client：在第三步中完成ServerSocketChannel注册到selector上。
                //完成一些runTask之后，就可以继续返回最外层循环的，继续进行selector.select().进行监听.
                if(!lbq.isEmpty()){   //队列是个啥东西啊？ 堆里的对象，线程的栈是独立，堆是共享的：所以在堆里的对象可以完成线程之间的通信。
                    //只有方法的逻辑，本地变量是线程隔离的
                    Channel c = lbq.take();
                    if(c instanceof ServerSocketChannel){
                        //注册监听的Socket：此时这个selector一定是前面自己选择的那个selector
                        ServerSocketChannel server = (ServerSocketChannel) c;
                        //刚开始注册的服务端的监听socket.
                        server.register(selector,SelectionKey.OP_ACCEPT);
                        System.out.println(Thread.currentThread().getName()+" register listen");
                    }else if(c instanceof  SocketChannel){
                        //后来客户端连接进来的时候，注册客户端的Socket.
                        SocketChannel client = (SocketChannel) c;
                        //客户端注册的时候，要绑定字节数组，供以后通信使用。
                        ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                        client.register(selector, SelectionKey.OP_READ, buffer);//表示以后关注的是读取事件
                        System.out.println(Thread.currentThread().getName()+" register client: " + client.getRemoteAddress());

                    }
                }



            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
//            catch (InterruptedException e) {
//                e.printStackTrace();
//            }


        }

    }

    private void readHander(SelectionKey key) {
        System.out.println(Thread.currentThread().getName()+" read......");
        //得到一个buffer.
        ByteBuffer buffer = (ByteBuffer)key.attachment();
        //客户端的Channel类型是SocketChannel
        SocketChannel client = (SocketChannel)key.channel();
        buffer.clear();
        while(true){
            try {
                //得到读到的字节.
                int num = client.read(buffer);
                if(num > 0){//读到了内容
                    buffer.flip();  //将读到的内容翻转，然后直接写出
                    while(buffer.hasRemaining()){
                        client.write(buffer);
                    }
                    //写完之后，buffer没有了，清除buffer内容.
                    buffer.clear();
                }else if(num == 0){//没有读到内容
                    break;
                }else if(num < 0 ){//客户端断开连接.
                    //客户端断开了
                    System.out.println("client: " + client.getRemoteAddress()+"closed......");
                    key.cancel();//当客户端断开连接时，会把FD从selector中移除掉.
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void acceptHandler(SelectionKey key) {
        System.out.println(Thread.currentThread().getName()+"   acceptHandler......");

        //服务端的类型是ServerSocketChannel
        ServerSocketChannel server = (ServerSocketChannel)key.channel();
        try {
            SocketChannel client = server.accept();
            client.configureBlocking(false);

            //单线程的时候，是注册到当前的selector中，多线程情况下，需要选择一个selector去注册。
            //choose a selector  and  register!!

//            stg.nextSelector(client);
            stg.nextSelectorV3(client);
//            stg.nextSelectorV2(client);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void setWorker(SelectorThreadGroup stgWorker) {
        this.stg =  stgWorker;
    }
}
