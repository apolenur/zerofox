package polenur.primes.jeromq;


import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;
import org.zeromq.ZMQ.Poller;

import com.google.protobuf.InvalidProtocolBufferException;

import polenur.primes.common.Logic;
import polenur.primes.common.Messages;
import polenur.primes.common.Messages.CalcPrimesReply;
import polenur.primes.common.Messages.CalcPrimesReply.Builder;
import polenur.primes.common.Messages.CalcPrimesRequest;

import java.util.Random;



public class asyncsrv
{

    private static Random rand = new Random(System.nanoTime());

    private static class client_task implements Runnable {

        public void run() {
            ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(ZMQ.DEALER);

            //  Set random identity to make tracing easier
            //String identity = String.format("%04X-%04X", rand.nextInt(), rand.nextInt());
            //client.setIdentity(identity.getBytes(ZMQ.CHARSET));
            client.connect("tcp://localhost:5570");

            PollItem[] items = new PollItem[] { new PollItem(client, Poller.POLLIN) };

            while (!Thread.currentThread().isInterrupted()) {
                //  Tick once per second, pulling in arriving messages
                for (int centitick = 0; centitick < 100; centitick++) {
                    ZMQ.poll(items, 10);
                    if (items[0].isReadable()) {
                        ZMsg msg = ZMsg.recvMsg(client);

                       CalcPrimesReply res = parseRes(msg.getLast().getData());
                       System.out.println("--->" + res);

                        msg.destroy();
                    }
                }
                client.send(buildReq(1,9));
            }
            ctx.destroy();
        }
    }

    private static class Dealer implements Runnable {

        public void run() {
            ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(ZMQ.DEALER);

            client.connect("tcp://localhost:5570");


            client.send(buildReq(1,9));
            client.send(buildReq(9,30));

            PollItem[] items = new PollItem[] { new PollItem(client, Poller.POLLIN) };

            int resNum = 0;
            int rangeNum = 2;
            
            while (resNum < rangeNum && !Thread.currentThread().isInterrupted() ) {
                //  Tick once per second, pulling in arriving messages
                for (int centitick = 0; centitick < 100; centitick++) {
                    ZMQ.poll(items, 10);
                    if (items[0].isReadable()) {
                        ZMsg msg = ZMsg.recvMsg(client);

                       CalcPrimesReply res = parseRes(msg.getLast().getData());
                       System.out.println("--->" + res);
                       resNum++;

                        msg.destroy();
                    }
                }

            }

            ctx.destroy();
        }
    }
    
    
    private static class SimpleDealer implements Runnable{

        public void run() {
            ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(ZMQ.DEALER);
            System.out.println("Dealer is ");

            client.bind("tcp://*:5570");

            System.out.println("Dealer is  up");

            client.send("", ZMQ.SNDMORE);
            client.send(buildReq(1,9));

            client.send("", ZMQ.SNDMORE);
            client.send(buildReq(9,30));

            System.out.println("Dealer is sent");
            
            client.recv();
            byte[] resData = client.recv();
            System.out.print(parseRes(resData));

            client.recv();
             resData = client.recv();
            System.out.print(parseRes(resData));

            ctx.destroy();
        }
    }

    private static class Worker implements Runnable {
        public void run() {
            ZContext ctx = new ZContext();

            Socket dealer = ctx.createSocket(ZMQ.REP);
            dealer.connect("tcp://localhost:5570");

            System.out.println("Worker is up");
            byte[] data = dealer.recv();
            CalcPrimesRequest calcReq = parseReq(data);
            System.out.println("-->" + calcReq);

            final Builder resMsgBuilder = Messages.CalcPrimesReply.newBuilder();

            Logic.processPrimesInRange(calcReq.getRangeStart(),
                    calcReq.getRangeEnd(), new Logic.PrimesProcessor() {

                        @Override
                        public void processPrime(int prime) {
                            resMsgBuilder.addPrimes(prime);

                        }
                    });
            
            
            
            dealer.send(resMsgBuilder.build().toByteArray());

            ctx.destroy();
        }
    }

    private static class server_task implements Runnable {
        public void run() {
            ZContext ctx = new ZContext();

            //  Frontend socket talks to clients over TCP
            Socket frontend = ctx.createSocket(ZMQ.ROUTER);
            frontend.bind("tcp://*:5570");

            //  Backend socket talks to workers over inproc
            Socket backend = ctx.createSocket(ZMQ.DEALER);
            backend.bind("inproc://backend");

            //  Launch pool of worker threads, precise number is not critical
            for (int threadNbr = 0; threadNbr < 5; threadNbr++)
                new Thread(new server_worker(ctx)).start();

            //  Connect backend to frontend via a proxy
            ZMQ.proxy(frontend, backend, null);

            ctx.destroy();
        }
    }


    private static class server_worker implements Runnable {
        private ZContext ctx;

        public server_worker(ZContext ctx) {
            this.ctx = ctx;
        }

        public void run() {
            Socket worker = ctx.createSocket(ZMQ.DEALER);
            worker.connect("inproc://backend");

            while (!Thread.currentThread().isInterrupted()) {
                // The DEALER socket gives us the address envelope and message
                ZMsg msg = ZMsg.recvMsg(worker);
                ZFrame addressFrame = msg.pop();
                ZFrame reqDataFrame = msg.pop();
                assert (reqDataFrame != null);
                msg.destroy();
                
                CalcPrimesRequest calcReq = parseReq(reqDataFrame.getData());
               
                final Builder resMsgBuilder = Messages.CalcPrimesReply.newBuilder();
                
                Logic.processPrimesInRange(calcReq.getRangeStart(),
                        calcReq.getRangeEnd(), new Logic.PrimesProcessor() {

                            @Override
                            public void processPrime(int prime) {
                                resMsgBuilder.addPrimes(prime);

                            }
                        });

                
                ZFrame resDataFrame = new ZFrame(resMsgBuilder.build().toByteArray());

                addressFrame.send(worker, ZFrame.REUSE + ZFrame.MORE);
                resDataFrame.send(worker, ZFrame.REUSE);
                
                addressFrame.destroy();
                reqDataFrame.destroy();
                resDataFrame.destroy();
            }
            ctx.destroy();
        }
    }
    

    //The main thread simply starts several clients, and a server, and then
    //waits for the server to finish.

    public static void main(String[] args) throws Exception {
        ZContext ctx = new ZContext();
        //new Thread(new client_task()).start();
        //new Thread(new client_task()).start();
        //new Thread(new client_task()).start();

//        new Thread(new Dealer()).start();
//        new Thread(new server_task()).start();


        new Thread(new Worker()).start();
        new Thread(new Worker()).start();
        new Thread(new SimpleDealer()).start();

        //  Run for 5 seconds then quit
        Thread.sleep(5 * 1000);
        ctx.destroy();
    }
    
    
    // Helper funcs
    static byte[] buildReq (int from, int to){
        
        return Messages.CalcPrimesRequest.newBuilder()
            .setRangeStart(from)
            .setRangeEnd(to)
            .build().toByteArray();
        
    }

    static CalcPrimesRequest parseReq (byte[] data){
        
        try {
            return Messages.CalcPrimesRequest.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    static CalcPrimesReply parseRes (byte[] data){
        
        try {
            return Messages.CalcPrimesReply.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
