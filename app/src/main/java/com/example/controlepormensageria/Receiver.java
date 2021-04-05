package com.example.controlepormensageria;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

public class Receiver extends AsyncTask {
    private final static String QUEUE_NAME = "controlePorMensageria";
    private String message;
    private LocalBroadcastManager broadcaster;
    private Context context;

    public Receiver(Context context){
        this.context=context;
        broadcaster = LocalBroadcastManager.getInstance(context);
    }

    @Override
    protected Object doInBackground(Object[] objects) {
        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUri("amqps://hmlhesbg:2e7CDYAULIV1Z3E7AQC9E7ov-h2biEOD@hornet.rmq.cloudamqp.com/hmlhesbg");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        Channel channel;
        try (Connection connection = factory.newConnection()) {
            channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

            //TODO: analisar autoAck True vs False com ack manual.
            channel.basicConsume(QUEUE_NAME, true, "",
                    new DefaultConsumer(channel) {
                        @Override
                        public void handleDelivery(String consumerTag,
                                                   Envelope envelope,
                                                   AMQP.BasicProperties properties,
                                                   byte[] body)
                                throws IOException
                        {
                            String routingKey = envelope.getRoutingKey();
                            String contentType = properties.getContentType();
                            long deliveryTag = envelope.getDeliveryTag();
                            message = new String(body, "UTF-8");
                            System.out.println(" [x] Received '" + message + "'");
                            enviaComando(message);
                            //channel.basicAck(deliveryTag, false);
                        }
                    }
            );
            System.out.println("consumido");
        } catch (TimeoutException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
        }
        return message;
    }

    private void enviaComando(String message){
        Intent intent = new Intent("comandoIntent");
        intent.putExtra ("comando", message);
        broadcaster.sendBroadcast(intent);
    }


}
