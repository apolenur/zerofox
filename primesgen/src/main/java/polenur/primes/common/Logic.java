package polenur.primes.common;

import java.util.LinkedList;
import java.util.List;

public class Logic {
    
   public static interface IntegerRangeProcessor{
       void processRange(int start, int end);
   }
   
    public static void processIntergersInChunks(int upperLimit, int numberOfChunks,
            IntegerRangeProcessor integerRangeProcessor) {

        int rangeLength = upperLimit % numberOfChunks == 0 ? upperLimit
                / numberOfChunks : upperLimit / numberOfChunks + 1;

        int currentRangeStart = 1;

        boolean isDone = false;


        while (!isDone) {
            int proposedRangeEnd = currentRangeStart + rangeLength;
            int currentRangeEnd = proposedRangeEnd;

            if (proposedRangeEnd >= upperLimit) {
                currentRangeEnd = upperLimit;
                isDone = true;
            }

            //System.out.println("[" + currentRangeStart + "," + currentRangeEnd + ")");

            integerRangeProcessor.processRange(currentRangeStart, currentRangeEnd);

            currentRangeStart = currentRangeEnd;
        }

    }
    
    /**
     * Returns list of all primes in [start, end) range 
     * @param start
     * @param end
     * @return
     */
    
    public static List<Integer> findPrimesInRange(int start, int end){
        
        final List<Integer> primes = new LinkedList();
        
        processPrimesInRange(start, end, 
                new PrimesProcessor() {
                    
                    @Override
                    public void processPrime(int prime) {
                        primes.add(prime);
                        
                    }
                });
        
        return primes;
    }
    
    public static interface PrimesProcessor{
        void processPrime(int prime);
    }

    public static void processPrimesInRange(int start, int end, PrimesProcessor processor){
        
        List<Integer> primes = new LinkedList();

        for(int i=start; i < end; i++){
           if(isPrime(i)){
               processor.processPrime(i);
           }
        }
        
    }

    public static boolean isPrime(int n) {
        if(n <= 0)
            throw new IllegalArgumentException("Notion of prime is only defined for positive integers");

        if( n == 1)
            return false;
        
        if(n == 2)
            return true;

        //check if n is a multiple of 2
        if (n%2==0) return false;
        //if not, then just check the odds
        for(int i=3;i*i<=n;i+=2) {
            if(n%i==0)
                return false;
        }
        return true;
    }
    
    

}
