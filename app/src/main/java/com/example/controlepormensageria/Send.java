package com.example.controlepormensageria;

import android.os.AsyncTask;
import android.widget.Toast;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

public class Send extends AsyncTask {

    private final static String QUEUE_NAME = "controlePorMensageria";

    @Override
    public Object doInBackground(Object[] objects) {

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

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            String message = "a";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
            System.out.println(" [x] Sent '" + message + "'");
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }

        return null;
    }
}
