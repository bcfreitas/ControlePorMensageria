package com.bcfreitas.controlepormensageria;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.util.concurrent.TimeoutException;

class MensageriaThread extends Thread {
    //responsável por receber dados da Thread principal
    //para enviar ao servidor rabbitMQ pelo protocolo AMQP
    public Handler mHandler;

    //variável para receber o contexto da aplicação, possibilidand enviar os dados
    //por intent recebidos do servidor rabbitMQ para thread principal
    private Context context;
    private Intent intent;
    private LocalBroadcastManager broadcaster;
    private String serialDrone;

    //nome da fila de mensagens no servidor rabbitMQ
    private final static String QUEUE_NAME = "controlePorMensageria";

    //canal para transmissão de dados contínua com o servidor rabbitMQ
    Channel channel;
    Channel channelEnvio;

    //fábrica de conexões rabbitMQ
    ConnectionFactory factory;

    //conexão com sevidor rabbitMQ
    Connection connection;
    Connection connectionEnvio;

    public MensageriaThread(Context context){
        this.context = context;
        this.broadcaster = LocalBroadcastManager.getInstance(context);
        this.intent = new Intent(ControleActivity.ACTION_MENSAGERIA);
    }

    public void run() {
        //inicializa a Thread como um loop, encerrando apenas com comando explícito
        Looper.prepare();

        if(this.factory==null){
            this.factory = new ConnectionFactory();
            try {
                factory.setUri("amqps://hmlhesbg:2e7CDYAULIV1Z3E7AQC9E7ov-h2biEOD@hornet.rmq.cloudamqp.com/hmlhesbg");
                if(this.connection == null){
                    this.connection=factory.newConnection();
                    this.connectionEnvio=factory.newConnection();
                };
                channel = connection.createChannel();
                channelEnvio = connectionEnvio.createChannel();
                channel.queueDeclare(QUEUE_NAME, true, false, false, null);
                channelEnvio.queueDeclare(QUEUE_NAME, true, false, false, null);
                System.out.println("Conexão e canal AMQP criado com sucesso!");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            //O Handler associado a thread é responsável
            //por receber mensagens de outras Threads para esta Thread
            mHandler = new Handler(Looper.myLooper()) {
                public void handleMessage(Message msg) {
                    //A thread principal envia o comando de teste
                    //envelopado em um bundle de dados numa Message com a key msgParaRabbitMQ
                    if(msg.getData().getString("msgParaRabbitMQ")!=null) {
                        System.out.println("entrou no if da msg para rabbitMQ");
                        enviaMensagemRabbitMQ(msg.getData().getString("msgParaRabbitMQ"));
                    }

                    if(msg.getData().getString("serialDrone")!=null){
                        atualizarSerialDrone(msg.getData().getString("serialDrone"));
                        Log.println(Log.INFO, "testesBruno", msg.getData().getString("serialDrone"));
                    }

                    Log.println(Log.INFO, "testesBruno", "a ThreadMensageria recebeu uma mensagem: " + msg.getData().toString());
                }
            };

            try {
                //abertura do canal contínuo de comunicação para recepção de dados
                //do servidor rabbitMQ pelo protocolo AMQP
                channel.basicConsume(QUEUE_NAME, false, "",
                        new DefaultConsumer(channel) {
                            @Override
                            //método chamado quando uma mensagem é recebida
                            //pelo canal com o servidor rabbitMQ
                            public void handleDelivery(String consumerTag,
                                                       Envelope envelope,
                                                       AMQP.BasicProperties properties,
                                                       byte[] body) {
                                try {
                                    long deliveryTag = envelope.getDeliveryTag();
                                    String amqpIncomingMessage = new String(body, "UTF-8");
                                    String[] arrayBody = amqpIncomingMessage.split(";");
                                    String droneDestino = arrayBody[0];
                                    //String comando = arrayBody[1];
                                    //String dataEnvio = arrayBody[2];
                                    String droneDestinoValue = droneDestino.split(":")[1];
                                    //String comandoValue = droneDestino.split(":")[2];
                                    //String dataEnvioValue = dataEnvio.split(":")[1];
                                    if(droneDestinoValue.equals(serialDrone)){
                                        enviaDadosParaThreadPrincipal(amqpIncomingMessage);
                                        Log.println(Log.INFO,"testesBruno","recebida msg com serial correto");
                                        System.out.println("Recebida mensagem rabbitMQ: " + amqpIncomingMessage + "'");
                                        channel.basicAck(deliveryTag, false);
                                    } else {
                                        Log.println(Log.INFO,"testesBruno","o serial aqui era: " + serialDrone + ". A msg na fila é para: " + droneDestino);
                                        enviaDadosParaThreadPrincipal("o serial aqui era: " + serialDrone + ". A msg na fila é para: " + droneDestino);
                                    }


                                } catch (Exception e) {
                                    Log.println(Log.ERROR, "testesBruno", e.getMessage() + e.getLocalizedMessage() + e.getCause().getLocalizedMessage() + e.toString());
                                };
                            }
                        }
                );
            } catch (IOException e) {
                e.printStackTrace();
            }

            //executa a fila de mensagens desta thread de forma contínua (permanece ativa,
            // aguardando mensagens via seu handler), após
            // declaração de operações que vão rodar na thread (método run)
            Looper.loop();
        }
    }

    /**
     * Envia dados para servidor RabbitMQ, com objetivo
     * de testes da conexão para o usuário
     *
     * @param message mensagem a ser enviada
     */
    private void enviaMensagemRabbitMQ(String message){
        System.out.println("entrou metodo envia msg rabbit");
        try {
            channelEnvio.basicPublish("", QUEUE_NAME, null, message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Envia dados da Thread da mensageria para Thread
     * principal do aplicativo
     *
     * @param message - String com mensagem para enviar à Thread principal
     */
    private void enviaDadosParaThreadPrincipal(String message){
        intent.removeExtra("comando");
        intent.putExtra ("comando", message);
        broadcaster.sendBroadcast(intent);
    }

    public void atualizarSerialDrone(String serial){
        this.serialDrone = serial;
    }
}

