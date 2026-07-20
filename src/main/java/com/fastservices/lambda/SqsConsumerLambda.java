package com.fastservices.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

public class SqsConsumerLambda implements RequestHandler<SQSEvent, Void> {
    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage msg : event.getRecords()) {
            context.getLogger().log("Mensaje SQS recibido desde la cola: " + msg.getBody());
            // Aquí se procesa el evento de manera asíncrona (Arquitectura Serverless)
        }
        return null;
    }
}