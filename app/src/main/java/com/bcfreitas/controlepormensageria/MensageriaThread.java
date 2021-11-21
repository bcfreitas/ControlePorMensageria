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
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private String filaParaMensageria;
    private static MensageriaThread mensageriaThreadInstance;
    public static final String FIWARE_QUEUE = "fiwareQueue";

    //canal para transmissão de dados contínua com o servidor rabbitMQ
    Channel channel;
    Channel channelEnvio;
    Channel channelEnvioFiware;

    //fábrica de conexões rabbitMQ
    ConnectionFactory factory;

    //conexão com sevidor rabbitMQ
    Connection connection;
    Connection connectionEnvio;
    Connection connectionEnvioFiware;
    private String urlRabbitmq;

    private MensageriaThread(){
        this.intent = new Intent(ControleActivity.ACTION_MENSAGERIA);
    }

    public static MensageriaThread getInstance(){
        if(mensageriaThreadInstance == null) {
            mensageriaThreadInstance = new MensageriaThread();
        }
        return mensageriaThreadInstance;
    }

    public void run() {
        //inicializa a Thread como um loop, encerrando apenas com comando explícito
        Looper.prepare();

        if(this.factory==null){
            this.factory = new ConnectionFactory();
            try {
                factory.setUri(urlRabbitmq);
                if(this.connection == null){
                    this.connection=factory.newConnection();
                    this.connectionEnvio=factory.newConnection();
                    this.connectionEnvioFiware=factory.newConnection();
                };
                channel = connection.createChannel();
                channelEnvio = connectionEnvio.createChannel();
                channelEnvioFiware = connectionEnvioFiware.createChannel();
                channel.queueDeclare(filaParaMensageria, true, false, false, null);
                channelEnvio.queueDeclare(filaParaMensageria, true, false, false, null);
                channelEnvioFiware.queueDeclare(FIWARE_QUEUE, true, false, false, null);
                Log.println(Log.INFO, "testesBruno", "Conexão e canal AMQP criado com sucesso! Na fila " + filaParaMensageria);
            } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException | TimeoutException | IOException e) {
                enviaDadosParaThreadPrincipal("Erro na conexão com o RabbitMQ! Verifique URL.");
            }

            //O Handler associado a thread é responsável
            //por receber mensagens de outras Threads para esta Thread
            mHandler = new Handler(Looper.myLooper()) {
                public void handleMessage(Message msg) {
                    if(connection == null || connectionEnvio == null || connectionEnvioFiware == null ) {
                        enviaDadosParaThreadPrincipal("Erro na conexão com o RabbitMQ! Verifique URL.");
                        return;
                    }
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

                    if(msg.getData().getString("fiwareServerData")!=null){
                        enviarDadosParaFiware(msg.getData().getString("fiwareServerData"));
                        Log.println(Log.INFO, "testesBruno", msg.getData().getString("fiwareServerData"));
                    }

                    Log.println(Log.INFO, "testesBruno", "a ThreadMensageria recebeu uma mensagem: " + msg.getData().toString());
                }
            };

            try {

                if(connection == null || connectionEnvio == null || connectionEnvioFiware == null ) {
                    enviaDadosParaThreadPrincipal("Erro na conexão com o RabbitMQ! Verifique URL.");
                    return;
                }

                //abertura do canal contínuo de comunicação para recepção de dados
                //do servidor rabbitMQ pelo protocolo AMQP
                channel.basicConsume(filaParaMensageria, false, "",
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
                                    Pattern pattern = Pattern.compile("[a-z]+\u003A([A-Z]|[0-9])+\u003B[a-z]+\u003A[0-9]{0,2}[a-z]+[0-9]?\u003B[a-z]+\u003A[0-9]+", Pattern.CASE_INSENSITIVE);
                                    Matcher matcher = pattern.matcher(amqpIncomingMessage);
                                    boolean matchFound = matcher.find();
                                    if (matchFound) {
                                        String[] arrayBody = amqpIncomingMessage.split(";");

                                        String droneDestino = arrayBody[0].split(":")[1];
                                        String comando = arrayBody[1].split(":")[1];
                                        String data = arrayBody[2].split(":")[1];

                                        if (droneDestino.equals(serialDrone)) {
                                            enviaDadosParaThreadPrincipal(comando);
                                            Log.println(Log.INFO, "testesBruno", "recebida msg com serial correto");
                                            channel.basicAck(deliveryTag, false);
                                        } else {
                                            Log.println(Log.INFO, "testesBruno", "o serial aqui era: " + serialDrone + ". A msg na fila é para: " + droneDestino);
                                            enviaDadosParaThreadPrincipal(comando + " [rejeitado, serial incorreto]");
                                            channel.basicNack(deliveryTag, false, false);
                                        }
                                    } else {
                                        Log.println(Log.INFO,"testesBruno","mensagem fora do padrão!");
                                        enviaDadosParaThreadPrincipal(amqpIncomingMessage + " [rejeitado, fora do padrão]");
                                        channel.basicNack(deliveryTag, false, false);
                                    }

                                } catch (Exception e) {
                                    Log.println(Log.ERROR, "testesBruno", e.getMessage() + e.getLocalizedMessage() + e.getCause().getLocalizedMessage() + e.toString());
                                }
                            }
                        }
                );
            } catch (IOException e) {
                Log.println(Log.ERROR, "testesBruno", e.getLocalizedMessage() + e.getCause());
                e.printStackTrace();
                enviaDadosParaThreadPrincipal("Erro na conexão com o RabbitMQ! Verifique URL.");
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
            channelEnvio.basicPublish("", filaParaMensageria, null, message.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Envia dados para servidor Fiware
     * para coleta por software que está controlando o drone
     *
     * @param message mensagem a ser enviada
     */
    private void enviarDadosParaFiware(String message){
        try {
            channelEnvioFiware.basicPublish("", FIWARE_QUEUE, null, message.getBytes());
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

    public void setBroadcaster(Context context) {
        this.broadcaster = LocalBroadcastManager.getInstance(context);
    }

    public void setFilaParaMensageria(String filaParaMensageria) {
        this.filaParaMensageria = filaParaMensageria;
    }

    public void setUrlRabbitmq(String urlRabbitmq) {
        this.urlRabbitmq=urlRabbitmq;
    }
}

