package com.bjmashibing.system.io.testreactor;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.Channel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: 马士兵教育
 * @create: 2020-06-21 20:37
 */
public class SelectorThreadGroup {  //天生都是boss

    SelectorThread[] sts;//每个Group中管理多个SelectorThread.
    //服务端的SelectorThread
    ServerSocketChannel serverSocketChannel=null;
    //通过一个院子变量来保证不同的客户端轮询的对应一个selector
    AtomicInteger xid = new AtomicInteger(0);

    //我们希望负责处理listen的在一个组里面，负责处理客户端连接的在一个组里面。
    //所以每个组还应该有其他的组的引用。
    //这就是netty中boss和worker的概念.
    //没有worker的时候，天生是boss。
    SelectorThreadGroup  stg =  this;

    public void setWorker(SelectorThreadGroup  stg){
        //如果当前这个组，没有对stg赋值，那么当前组应该就是boss.用的就是当前组的sts.
        //要是赋值之后，那么当前组就是worker，所以在worker组中用的sts是stg.sts
        //server应该是在当前组去选择selector , 而不是在worker中选择。
        //我们要在boss组中注册listen，在worker组中选择注册客户端.
        this.stg =  stg;

    }

    SelectorThreadGroup(int num){
        //num  线程数
        sts = new SelectorThread[num];
        for (int i = 0; i < num; i++) {
            sts[i] = new SelectorThread(this);
            //在多线程的情况下，让每个selector对应的线程跑起来.
            new Thread(sts[i]).start();
        }

    }


    /**
     * 在主线程中被调用，
     * 问题是serverSocketChannel需要被注册到哪个selector上呢？
     * @param port
     */
    public void bind(int port) {

        try {
            //serverSocketChannel的初始化
            serverSocketChannel =  ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.bind(new InetSocketAddress(port));

            //server需要注册到那个selector上呢？选择一个selector注册自己的serverSocketChannel
//            nextSelectorV2(serverSocketChannel);
            nextSelectorV3(serverSocketChannel);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void nextSelectorV3(Channel c) {

        try {
            if(c instanceof  ServerSocketChannel){
                //server注册到当前组的selector，所以应该在自己组中挑一个selector.
                SelectorThread st = next();  //listen 选择了 boss组中的一个线程后，要更新这个线程的work组
                st.lbq.put(c);
                st.setWorker(stg);
                st.selector.wakeup();
            }else {
                SelectorThread st = nextV3();  //在 main线程种，取到堆里的selectorThread对象

                //1,通过队列传递数据 消息
                st.lbq.add(c);
                //2,通过打断阻塞，让对应的线程去自己在打断后完成注册selector
                st.selector.wakeup();

            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }


    public void nextSelectorV2(Channel c) {

            try {
                //如果是listen的socket ,就直接注册到0号这个selector上.
                if(c instanceof  ServerSocketChannel){
                sts[0].lbq.put(c);
                sts[0].selector.wakeup();
                }else {
                    //客户端的socket ,全部在0号以外的selector中注册.
                    SelectorThread st = nextV2();  //在 main线程种，取到堆里的selectorThread对象

                    //1,通过队列传递数据 消息
                    st.lbq.add(c);
                    //2,通过打断阻塞，让对应的线程去自己在打断后完成注册selector
                    st.selector.wakeup();

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }


    }

    /**
     * 将serverSocketChannel注册到某个selector上...
     * @param c: 该方法既可以将server注册到某个selector上，也可以将client注册到某个selector上。
     *         所以这里的参数类型是Channel.
     *         Channel 是 ServerSocketChannel和SocketChannel的父类.
     */
    public void nextSelector(Channel c) {
        //找到了待注册的selector.
        SelectorThread st = next();  //在 main线程种，取到堆里的selectorThread对象

        //1,通过队列传递数据 消息
        st.lbq.add(c);
        //2,通过打断阻塞，让selector中的线程跳过本次的selector.select()循环.
        st.selector.wakeup();

        //寻找下一个可以注册Channel的Selector.
        //wakeup()放到注册之前和注册之后是有差异的.
//    public void nextSelector(Channel c) {
//        SelectorThread st = next();  //在 main线程种，取到堆里的selectorThread对象
//
//        //1,通过队列传递数据 消息.将当前的Channel交给selector的队列，让selector处理同步问题。
//        st.lbq.add(c);
//        //2,通过打断selector线程的阻塞，让对应的线程去自己在打断后完成注册selector
//        st.selector.wakeup();


        //重点：  c有可能是 serverSocketChannel  有可能是client
//        ServerSocketChannel s = (ServerSocketChannel) c;
        //呼应上， int nums = selector.select();  //阻塞  wakeup()
//        try {
//            s.register(st.selector, SelectionKey.OP_ACCEPT);  //会被阻塞的!!!!!
//            st.selector.wakeup();  //功能是让 selector的select（）方法，立刻返回，不阻塞！
//            System.out.println("aaaaa");
//        } catch (ClosedChannelException e) {
//            e.printStackTrace();
//        }

    }



    //无论 serversocket  socket  都复用这个方法
    //轮询的找到sts这个数组中的某一个selector,可以把server和client放到这个selector中.
    private SelectorThread next() {
        int index = xid.incrementAndGet() % sts.length;  //轮询就会很尴尬，倾斜
        return sts[index];
    }

    //去除0号selector，然后开始轮询，后面的客户端，全部在0号以外的客户端来轮询注册.
    private SelectorThread nextV2() {
        int index = xid.incrementAndGet() % (sts.length-1);  //轮询就会很尴尬，倾斜
        return sts[index+1];
    }

    private SelectorThread nextV3() {
        int index = xid.incrementAndGet() % stg.sts.length;  //动用worker的线程分配
        return stg.sts[index];
    }
}
