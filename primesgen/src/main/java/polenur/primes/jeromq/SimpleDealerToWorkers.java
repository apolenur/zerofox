package polenur.primes.jeromq;


import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import polenur.primes.common.Logic;
import polenur.primes.common.Messages;
import polenur.primes.common.MessagesHelper;
import polenur.primes.common.Messages.CalcPrimesReply;
import polenur.primes.common.Messages.CalcPrimesReply.Builder;
import polenur.primes.common.Messages.CalcPrimesRequest;

import java.util.LinkedList;
import java.util.List;


public class SimpleDealerToWorkers
{
    
    private static class Dealer implements Runnable{
        
        private int numberOfChunks;
        private int primesUpperLimit;

        public Dealer(int primesUpperLimit, int numberOfChunks) {
            this.numberOfChunks = numberOfChunks;
            this.primesUpperLimit = primesUpperLimit;
        }

        public void run() {

            long startTime = System.nanoTime();

            ZContext ctx = new ZContext();
            final Socket workers = ctx.createSocket(ZMQ.DEALER);

            workers.bind("tcp://*:5570");
            
            //send all calculation tasks
            Logic.processIntergersInChunks(primesUpperLimit, numberOfChunks,
                    new Logic.IntegerRangeProcessor() {

                        @Override
                        public void processRange(int start, int end) {
                            sendCalcRequest(workers, start, end);
                        }
                    });
            
           //collect results
            List<Integer> primes = new LinkedList();
            
            for (int i = 0; i < numberOfChunks; i++) {
                List<Integer> primesChunk = recvCalcResponse(workers);
                primes.addAll(primesChunk);
            }

            printPrimes(primes);
            
            long elapsedTime = System.nanoTime() - startTime;
            
            System.out.println("\n \t Done .Total execution time in ms: "
                    + elapsedTime/1000000);
            
            System.out.println("\n\t Please press Cntrl-C to exit");

            ctx.destroy();
        }
        
        private void sendCalcRequest(Socket workers, int start, int end){
            //reply socket expects empty frame before data frame
            //see http://zguide.zeromq.org/page:all#The-DEALER-to-REP-Combination

            workers.send("", ZMQ.SNDMORE);
            workers.send(MessagesHelper.buildReq(start,end));
        }
        
        private List<Integer> recvCalcResponse(Socket workers){
            //discard first frame before reading the data
            //see http://zguide.zeromq.org/page:all#The-DEALER-to-REP-Combination
            workers.recv();
            byte[] resData = workers.recv();
            CalcPrimesReply calcRes = MessagesHelper.parseRes(resData);

            return calcRes.getPrimesList();
        }
        
        private void printPrimes(List<Integer> primes){

          System.out.println("== Primes ==");

          for(int p : primes){
              System.out.println(p);
          }
        }
    }

    private static class Worker implements Runnable {
       
        //worker id used for debugging 
        private String id;
       
        
        public Worker(String id) {
            this.id = id;
        }

        public void run() {
            ZContext ctx = new ZContext();

            Socket dealer = ctx.createSocket(ZMQ.REP);
            dealer.connect("tcp://localhost:5570");
            
            while (!Thread.currentThread().isInterrupted()) {
                byte[] data = dealer.recv();
                CalcPrimesRequest calcReq = MessagesHelper.parseReq(data);

                System.out.println(id + " processing req\n" + calcReq);

                final Builder resMsgBuilder = Messages.CalcPrimesReply
                        .newBuilder();

                Logic.processPrimesInRange(calcReq.getRangeStart(),
                        calcReq.getRangeEnd(), new Logic.PrimesProcessor() {

                            @Override
                            public void processPrime(int prime) {
                                resMsgBuilder.addPrimes(prime);

                            }
                        });

                dealer.send(resMsgBuilder.build().toByteArray());
            }

            ctx.destroy();
        }
    }


    public static void main(String[] args) throws Exception {
        
        if(args.length < 1 || args.length > 2){
            printUsage();
            System.exit(1);
        }


        int primeUpperLimit = Integer.parseInt(args[0]);
        int numberOfChunks = 2;
        
        if (args.length == 2)
            numberOfChunks = Integer.parseInt(args[1]);
        
        
        ZContext ctx = new ZContext();
        

        int numberOfWorkers = numberOfChunks;
        //int numberOfWorkers = 1 ;

        System.out.println("\n\t Calculating primes up to: " + primeUpperLimit + 
                            "\n\t Number of workers: " + numberOfWorkers + '\n');

        for (int w = 0; w < numberOfWorkers; w++) {
            new Thread(new Worker("wrkr"+ w)).start();
        }

        new Thread(new Dealer(primeUpperLimit, numberOfChunks)).start();

        //Thread.sleep(5 * 1000);
        ctx.destroy();
    }


    private static void printUsage() {
        System.out
                .println("At least one argument is required - upper limit for primes \n"
                        + "Optinally second can be provided to specifies number of workers. Default 2");
    }
    
    
}
