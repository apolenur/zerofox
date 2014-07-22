package polenur.primes.common;

import polenur.primes.common.Messages.CalcPrimesReply;
import polenur.primes.common.Messages.CalcPrimesRequest;

import com.google.protobuf.InvalidProtocolBufferException;

public class MessagesHelper {
    public static byte[] buildReq (int from, int to){
        
        return Messages.CalcPrimesRequest.newBuilder()
            .setRangeStart(from)
            .setRangeEnd(to)
            .build().toByteArray();
        
    }

    public static CalcPrimesRequest parseReq (byte[] data){
        
        try {
            return CalcPrimesRequest.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    public static CalcPrimesReply parseRes (byte[] data){
        
        try {
            return Messages.CalcPrimesReply.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

}
