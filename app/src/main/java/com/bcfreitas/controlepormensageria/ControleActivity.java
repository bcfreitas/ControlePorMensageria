package com.bcfreitas.controlepormensageria;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.battery.BatteryState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class ControleActivity extends AppCompatActivity {

    Handler handler = new Handler(Looper.getMainLooper());
    int tempoParaConsulta = 2000;
    boolean sincronizacaoAtiva;
    Collection<Integer> estados = new ArrayList<Integer>();
    Integer ultimoComando;
    Date dataUltimoComando;
    Integer tempoPadraoComando = 1000;
    private MensageriaThread mensageriaThread;

    private boolean yawControlModeFlag = true;
    private boolean rollPitchControlModeFlag = true;
    private boolean verticalControlModeFlag = true;
    private boolean horizontalCoordinateFlag = true;
    private Timer sendVirtualStickDataTimer;
    private SendVirtualStickDataTask sendVirtualStickDataTask;
    private SendTakeOff sendTakeOff;
    private SendLanding sendLanding;
    private TurnOnMotors turnOnMotors;
    private TurnOffMotors turnOffMotors;
    private GetBatteryLevel getBatteryLevel;
    private int nivelBateria;
    private String serialNumber;
    public static String ACTION_SERIAL_DRONE = "com.bcfreitas.controlepormensageria.ACTION_SERIAL_DRONE";
    public static String ACTION_MENSAGERIA = "com.bcfreitas.controlepormensageria.ACTION_MENSAGERIA";
    public String filaParaMensageria;

    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;

    public static final int DURACAO_PADRAO_EM_DECISEGUNDOS = 10;
    public static final int INTERVALO_DE_ENVIO_PADRAO = 10;
    public static final int VALOR_PADRAO = 5;
    public int rollPitchControlMode;


    private BroadcastReceiver broadcastConexaoDrone = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            consultarSerialDrone();
        }
    };

    private BroadcastReceiver broadcastNumeroSerial = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(ControleActivity.ACTION_SERIAL_DRONE)){
                atualizarViewSerial((String)intent.getExtras().get("serial"));
            }
        }
    };

    private BroadcastReceiver broadcastMensageria = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getExtras().getString("comando")!=null) {
                String comando = intent.getExtras().getString("comando");


                Integer valorExplicito = VALOR_PADRAO; //valor padrao = 5
                String acao;
                Integer duracaoEmDecisegundos = DURACAO_PADRAO_EM_DECISEGUNDOS; //tempo padrao = 1s

                //quando vem [valorExplicito][acao][duracao]
                if(comando.matches("[0-9]{1,2}[a-z]+[0-9]")) {
                    valorExplicito = Integer.valueOf(comando.split("[a-z]+")[0]);
                    acao = comando.replaceAll("[0-9]+", "");
                    duracaoEmDecisegundos = Integer.valueOf(comando.split("[a-z]+")[1]);
                //quando vem [valorExplicito][acao]
                } else if(comando.matches("[0-9][a-z]+")){
                    valorExplicito = Integer.valueOf(comando.replaceAll("[a-z]+",""));
                    acao = comando.replaceAll("[0-9]+", "");
                //quando vem [acao][duracao]
                } else if(comando.matches("[a-z]+[0-9]")) {
                    acao = comando.replaceAll("[0-9]", "");
                    duracaoEmDecisegundos = Integer.valueOf(comando.replaceAll("[a-z]+",""));
                //quando vem apenas [acao] ou qualquer outra coisa
                } else {
                    acao = comando;
                }

                if (acao.equals("a")) {
                    recebeComandoNavegacao(R.id.botao_esquerda, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("d")) {
                    recebeComandoNavegacao(R.id.botao_direita, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("w")) {
                    recebeComandoNavegacao(R.id.botao_frente, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("s")) {
                    recebeComandoNavegacao(R.id.botao_tras, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("t")) {
                    recebeComandoNavegacao(R.id.botao_takeoff, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("l")) {
                    recebeComandoNavegacao(R.id.botao_land, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("g")) {
                    recebeComandoNavegacao(R.id.botao_girar, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("on")) {
                    recebeComandoNavegacao(R.id.botao_on, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("off")) {
                    recebeComandoNavegacao(R.id.botao_off, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("bat")) {
                    recebeComandoNavegacao(R.id.botao_bateria, duracaoEmDecisegundos, valorExplicito);
                } else {
                    Toast.makeText(getApplicationContext(), comando, Toast.LENGTH_SHORT).show();
                    //Registra log do comando recebido na TextView da tela
                    TextView logView = findViewById(R.id.logComandos);
                    registraLog(logView, acao);
                }
            }
        }
    };

    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver((broadcastMensageria),
                new IntentFilter(ControleActivity.ACTION_MENSAGERIA)
        );
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver((broadcastNumeroSerial),
                new IntentFilter(ControleActivity.ACTION_SERIAL_DRONE)
        );
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver((broadcastConexaoDrone),
                new IntentFilter(MainActivity.ACTION_CONEXAO_DRONE)
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(broadcastMensageria);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(broadcastNumeroSerial);
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(broadcastConexaoDrone);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        filaParaMensageria = (String) intent.getExtras().get("canalParaMensageria");

        setContentView(R.layout.activity_controle);
        estados.add(R.id.botao_frente);
        estados.add(R.id.botao_centro);
        estados.add(R.id.botao_tras);
        estados.add(R.id.botao_esquerda);
        estados.add(R.id.botao_direita);
        estados.add(R.id.botao_takeoff);
        estados.add(R.id.botao_girar);
        estados.add(R.id.botao_land);
        estados.add(R.id.botao_on);
        estados.add(R.id.botao_off);
        estados.add(R.id.botao_bateria);

        mensageriaThread = MensageriaThread.getInstance();
        mensageriaThread.setBroadcaster(getApplicationContext());
        mensageriaThread.setFilaParaMensageria(filaParaMensageria);
        mensageriaThread.start();

        handler.postDelayed(new Runnable() {
            public void run() {
                consultarSerialDrone();
            }
        }, 5000);

        handler.postDelayed(new Runnable() {
            public void run() {
                atualizarAcaoBotaoEnviar();
            }
        }, 6000);

        findViewById(R.id.botao_bateria).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_bateria, 1, 1);
            }
        });

        findViewById(R.id.botao_on).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_on, 1, 1);
            }
        });

        findViewById(R.id.botao_off).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_off, 1, 1);
            }
        });

        findViewById(R.id.botao_takeoff).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_takeoff, 1, 1);
            }
        });

        findViewById(R.id.botao_land).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_land, 3, 1);
            }
        });

        findViewById(R.id.botao_girar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_girar, 3, 1);
            }
        });

        findViewById(R.id.botao_frente).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_frente, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });

        findViewById(R.id.botao_tras).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_tras, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });

        findViewById(R.id.botao_esquerda).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_esquerda, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });

        findViewById(R.id.botao_direita).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_direita, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });

        findViewById(R.id.botao_centro).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_centro, DURACAO_PADRAO_EM_DECISEGUNDOS, 0);
            }
        });

        Switch virtualStickSwitch = ((Switch) findViewById(R.id.virtualStick));
        virtualStickSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isFlightControllerAvailable()) {
                    if (isChecked) {
                        toggleVirtualStick(true,"VirtualStick ativado!");
                    } else {
                        toggleVirtualStick(false, "VirtualStick desativado!");
                    }
                } else {
                    showToast("O drone não está conectado.");
                    ((Switch) findViewById(R.id.virtualStick)).setChecked(false);
                }
            }
        });

        Spinner rollPitchModeSelect = ((Spinner) findViewById(R.id.rollPitchModeSelect));
        rollPitchModeSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id ) {
                String labelRollPitchControlMode;
                labelRollPitchControlMode = (String) ((Spinner) findViewById(R.id.rollPitchModeSelect)).getSelectedItem();
                if(isFlightControllerAvailable()){
                    if(labelRollPitchControlMode.equals("ANGLE")){
                        getAircraftInstance().getFlightController().setRollPitchControlMode(RollPitchControlMode.ANGLE);
                    } else {
                        getAircraftInstance().getFlightController().setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                    }
                    showToast("Modo de vôo configurado no drone!");
                    getAircraftInstance().getFlightController().setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                    atualizarViewRollPitchMode();
                } else {
                    showToast("Drone não está conectado!");
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                atualizarViewRollPitchMode();
            }
        });
    }

    private void toggleVirtualStick(boolean b, String s) {
        getAircraftInstance().getFlightController().setVirtualStickModeEnabled(b, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    showToast(s);
                    ((Switch)findViewById(R.id.virtualStick)).setChecked(b);
                } else {
                    showToast(djiError.getErrorCode() + " - " + djiError.getDescription());
                }
            }
        });
    }

    public void atualizarAcaoBotaoEnviar() {
        findViewById(R.id.btnSendMessage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Enviando mensagem para o servidor rabbitMQ.", Toast.LENGTH_SHORT).show();
                Message msg2 = new Message();
                Bundle bundleSample = new Bundle();
                String dadosParaServidor = "drone:" + serialNumber + ";comando:" + ((TextView) findViewById(R.id.inputMsgRabbitMQ)).getText().toString().trim() + ";data:" + System.currentTimeMillis();
                bundleSample.putString("msgParaRabbitMQ", dadosParaServidor);
                msg2.setData(bundleSample);
                mensageriaThread.mHandler.sendMessage(msg2);
            }
        });
    }

    //adiciona log do comando à TextView da tela, para consulta.
    private void registraLog(TextView logView, int direcao, Integer duracaoEmDecisegundos, Integer valorExplicito) {
        String textoDirecao;
        switch (direcao){
            case R.id.botao_direita:
                textoDirecao =  valorExplicito + " para direita por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_esquerda:
                textoDirecao = valorExplicito + " para esquerda por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_frente:
                textoDirecao = valorExplicito + " para frente por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_tras:
                textoDirecao = valorExplicito + " para trás  por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_centro:
                textoDirecao = "pare!";
                break;
            case R.id.botao_takeoff:
                textoDirecao = "levantar vôo";
                break;
            case R.id.botao_girar:
                textoDirecao = valorExplicito + " girar por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_land:
                textoDirecao = "pousar";
                break;
            case R.id.botao_on:
                textoDirecao = "ligar motor";
                break;
            case R.id.botao_off:
                textoDirecao = "desligar motor";
                break;
            case R.id.botao_bateria:
                textoDirecao = "checar bateria";
                break;
            default:
                textoDirecao = "";
        }
        String log = (String) logView.getText();
        DateFormat df = DateFormat.getInstance();
        log = log + "\n" + (df.format(new Date())) + " - " + textoDirecao;
        logView.setText(log);
        ((ScrollView) findViewById(R.id.scrollLog)).fullScroll(View.FOCUS_DOWN);
    }

    public void registraLog(TextView logView, String comando) {
        String log = (String) logView.getText();
        DateFormat df = DateFormat.getInstance();
        log = log + "\n" + (df.format(new Date())) + " - " + comando;
        logView.setText(log);
        ((ScrollView) findViewById(R.id.scrollLog)).fullScroll(View.FOCUS_DOWN);
    }

    public void recebeComandoNavegacao(int direcao, Integer duracaoEmDecisegundos, Integer valorExplicito) {
        //Executa ação/comando
        executaComando(direcao);
        this.ultimoComando = direcao;
        dataUltimoComando = new Date();

        //Registra log do comando recebido na TextView da tela
        TextView logView = findViewById(R.id.logComandos);
        registraLog(logView, direcao, duracaoEmDecisegundos, valorExplicito);

        //Cria thread para voltar ao estado parado após o intervalo de tempo definido
        handler.postDelayed(new Runnable() {
            public void run() {
                retornaEstadoParado();
            }
        }, tempoPadraoComando);

        enviaComandoParaDrone(direcao, duracaoEmDecisegundos, valorExplicito);
    }

    public void enviaComandoParaDrone(int direcao, Integer duracaoEmDecisegundos, Integer valorExplicito){
        //Coordenadas X e Y do controle virtual anaĺogico esquerdo (girar, subir/descer - eixo Z )
        float controleEsquerdo_pX = 0;
        float controleEsquerdo_pY = 0;
        //Coordenadas X e Y do controle virtual anaĺogico direito (frente, trás, esquerda,
        //  direita - eixos X/Y)
        float controleDireito_pX = 0;
        float controleDireito_pY = 0;

        switch (direcao) {
            case R.id.botao_direita:
                controleDireito_pX = valorExplicito;
                break;
            case R.id.botao_esquerda:
                controleDireito_pX = -1*valorExplicito;
                break;
            case R.id.botao_frente:
                controleDireito_pY = -1*valorExplicito;
                break;
            case R.id.botao_tras:
                controleDireito_pY = valorExplicito;
                break;
            case R.id.botao_centro:
                controleEsquerdo_pX = 0;
                controleEsquerdo_pY = 0;
                controleDireito_pX = 0;
                controleDireito_pY = 0;
                break;
            case R.id.botao_takeoff:
                sendTakeOff = new SendTakeOff();
                sendVirtualStickDataTimer = new Timer();
                sendVirtualStickDataTimer.schedule(sendTakeOff, new Date());
                return;
            case R.id.botao_girar:
                //único giro implementado é para esquerda
                controleEsquerdo_pX = -1*valorExplicito;
                break;
            case R.id.botao_land:
                sendLanding = new SendLanding();
                sendVirtualStickDataTimer = new Timer();
                sendVirtualStickDataTimer.schedule(sendLanding, new Date());
                return;
            case R.id.botao_on:
                turnOnMotors = new TurnOnMotors();
                sendVirtualStickDataTimer = new Timer();
                sendVirtualStickDataTimer.schedule(turnOnMotors, new Date());
                return;
            case R.id.botao_off:
                turnOffMotors = new TurnOffMotors();
                sendVirtualStickDataTimer = new Timer();
                sendVirtualStickDataTimer.schedule(turnOffMotors, new Date());
                return;
            case R.id.botao_bateria:
                getBatteryLevel = new GetBatteryLevel();
                sendVirtualStickDataTimer = new Timer();
                sendVirtualStickDataTimer.schedule(getBatteryLevel, new Date());
                return;
            default:
                controleEsquerdo_pX = 0;
                controleEsquerdo_pY = 0;
                controleDireito_pX = 0;
                controleDireito_pY = 0;
        }

        //flag setada como true por padrão, fazendo com que
        // pitch (passo, frente/trás): represente posição do eixo X do drone (eixo Y do virtual stick direito)
        // roll (rolar lateralmente): represente posição do eixo Y do drone (eixo X do virtual stick direito)
        if (horizontalCoordinateFlag) {
            if (rollPitchControlModeFlag) {
                pitch = (float) (controleDireito_pY);
                roll = (float) (controleDireito_pX);
            } else {
                pitch = -(float) (controleDireito_pX);
                roll = (float) (controleDireito_pY);
            }
        }

        //yaw (guinada/giro) representa o giro do drone, tem dois modos:
        // modo velocidade angular, em que o valor passado é a quantidade de graus por segundo,
        // modo ângulo, em que o valor passado é o ângulo para girar.
        yaw = controleEsquerdo_pX;

        //throtle representa o movimento vertical (eixo Z), tem dois modos:
        // modo posição: valor que representa a altitude em relação à posição de decolagem,
        // modo velocidade: valores positivos representam ascenção, negativos descida.
        throttle = controleEsquerdo_pY;

        //Aqui é criada a classe que vai ser agendada para executar e enviar uma instância de
        // FlightControlData com os dados de navegação pitch, roll, yaw e throttle atribuídos acima.

        if (isFlightControllerAvailable()) {
            toggleVirtualStick(true, "VirtualStick ativado");
            sendVirtualStickDataTask = new SendVirtualStickDataTask();
            sendVirtualStickDataTimer = new Timer();
            sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 100); //100ms permite 10 vezes/s = 10Hz
            showToast("Comando de movimento enviado continuamente a uma frequência de 10Hz.");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendVirtualStickDataTimer.cancel();
                    toggleVirtualStick(false, "VirtualStick desativado!");
                    showToast("Comando de movimento (10Hz) finalizado.");
                }
            }, duracaoEmDecisegundos*100);
        } else {
            showToast("Drone não está conectado!");
        }
    }

    private class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            try {
                FlightControlData flightControlData = new FlightControlData(pitch,
                        roll,
                        yaw,
                        throttle);
                if (isFlightControllerAvailable()) {
                    getAircraftInstance()
                            .getFlightController()
                            .sendVirtualStickFlightControlData(flightControlData,
                                    new CommonCallbacks.CompletionCallback() {
                                        @Override
                                        public void onResult(DJIError djiError) {
                                            if(djiError!=null) {
                                                showToast((djiError.getDescription()) + String.valueOf(djiError.getErrorCode()));
                                            }
                                        }
                                    });
                }

                registraLogSDK("", flightControlData);
            } catch (Exception e) {
                showToast("Erro :" + e.getMessage());
            }
        }
    }

    private class SendTakeOff extends TimerTask {

        @Override
        public void run() {
            try {
                if (isFlightControllerAvailable()) {
                    getAircraftInstance()
                            .getFlightController().startTakeoff(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null) {
                                        showToast((djiError.getDescription()) + String.valueOf(djiError.getErrorCode()));
                                    } else {
                                        showToast("takeOff enviado para drone com Sucesso! O drone deve estar no ar neste momento.");
                                    }
                                }
                            });
                } else {
                    showToast("Drone não está conectado!!!");
                }
                registraLogSDK(" startTakeOff", null);
            } catch (Exception e) {
                showToast("Erro: " + e.getMessage());
            }
        }
    }

    private class TurnOnMotors extends TimerTask {

        @Override
        public void run() {
            try {
                if (isFlightControllerAvailable()) {
                    getAircraftInstance()
                            .getFlightController().turnOnMotors(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null) {
                                        showToast((djiError.getDescription()) + String.valueOf(djiError.getErrorCode()));
                                    } else {
                                        showToast("turnOn enviado para drone com Sucesso! Olha aí que o bicho deve estar girando! :)");
                                    }
                                }
                            });
                } else {
                    showToast("Drone não está conectado!!!");
                }
                registraLogSDK(" turnOnMotors", null);
            } catch (Exception e) {
                showToast("Erro: " + e.getMessage());
            }
        }
    }

    private class TurnOffMotors extends TimerTask {

        @Override
        public void run() {
            try {
                if (isFlightControllerAvailable()) {
                    getAircraftInstance()
                            .getFlightController().turnOffMotors(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null) {
                                        showToast((djiError.getDescription()) + String.valueOf(djiError.getErrorCode()));
                                    } else {
                                        showToast("turnOff enviado para drone com Sucesso! As hélices devem estar paradas.");
                                    }
                                }
                            });
                } else {
                    showToast("Drone não está conectado!!!");
                }
                registraLogSDK(" turnOffMotors", null);
            } catch (Exception e){
                showToast("Erro: " + e.getMessage());
            }
        }
    }

    private class SendLanding extends TimerTask {

        @Override
        public void run() {
            try {
                if (isFlightControllerAvailable()) {
                    getAircraftInstance()
                            .getFlightController().startLanding(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null) {
                                        showToast((djiError.getDescription()) + String.valueOf(djiError.getErrorCode()));
                                    } else {
                                        showToast("sendLand enviado para drone com Sucesso! Deve ter pousado.");
                                    }
                                }
                            });
                } else {
                    showToast("Drone não está conectado!!!");
                }
                registraLogSDK(" startLanding", null);
            } catch (Exception e) {
                showToast("Exception: " + e.getMessage());
            }
        }
    }

    private class GetBatteryLevel extends TimerTask {

        @Override
        public void run() {
            try {
                if (isFlightControllerAvailable()) {
                    checarBateria();
                    registraLogSDK("BatteryState.getChargeRemainingInPercent()", null);
                } else {
                    showToast("Drone não conectado ou detectado!");
                }
            } catch (Exception e) {
                showToast("Exception: " + e.getMessage());
            }
        }
    }

    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static boolean isFlightControllerAvailable() {
        return isProductModuleAvailable() && isAircraft() &&
                (null != getAircraftInstance() &&
                (null != getAircraftInstance().getFlightController()));
    }

    public static boolean isProductModuleAvailable() {
        return (null != getProductInstance());
    }

    public static boolean isAircraft() {
        return getProductInstance() instanceof Aircraft;
    }

    /**
     * Gets instance of the specific product connected after the
     * API KEY is successfully validated. Please make sure the
     * API_KEY has been added in the Manifest
     */
    public static synchronized BaseProduct getProductInstance() {
        return DJISDKManager.getInstance().getProduct();
    }

    public static synchronized Aircraft getAircraftInstance() {
        if (!isAircraftConnected()) {
            return null;
        }
        return (Aircraft) getProductInstance();
    }

    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof Aircraft;
    }

    //executa a ação referente ao comando recebido
    public void executaComando(int direcao){
        //Desativa todas as ações/estados anteriores
        Iterator iteratorEstados = this.estados.iterator();
        while(iteratorEstados.hasNext()) {
            findViewById((Integer)iteratorEstados.next()).setBackgroundColor(getResources().getColor(R.color.purple_500));
        }
        //Ativa novo estado associado ao comando recebido
        findViewById(direcao).setBackgroundColor(getResources().getColor(R.color.teal_200));
    }

    //chamado após o tempo definido de ação
    public void retornaEstadoParado(){
        //Só deve retornar ao estado inicial após o tempo determinado da última ação
        if((new Date()).getTime() >= dataUltimoComando.getTime()+tempoPadraoComando)   {
            //Desativa todas as ações/estados anteriores
            Iterator iteratorEstados = this.estados.iterator();
            while (iteratorEstados.hasNext()) {
                findViewById((Integer) iteratorEstados.next()).setBackgroundColor(getResources().getColor(R.color.purple_500));
            }
            //fica no estado padrão
            findViewById(R.id.botao_centro).setBackgroundColor(getResources().getColor(R.color.teal_200));
        }
    }

    public void registraLogSDK(String levantarOuPousar, FlightControlData flightControlData){
        Log.println(Log.INFO, "testesBruno", "chamou registra log SDK");
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                TextView logViewSDK = findViewById(R.id.logComandosSDK);
                String log = (String) logViewSDK.getText();
                DateFormat df = DateFormat.getInstance();
                if(flightControlData!=null) {
                    log = log + "\n" + (df.format(new Date())) + " Pitch:" + flightControlData.getPitch() + ", Roll:" +
                            flightControlData.getRoll() + ", Yaw:" + flightControlData.getYaw() + ", Throttle: " + flightControlData.getVerticalThrottle();
                } else {
                    log = log + "\n" + (df.format(new Date())) + levantarOuPousar;
                }

                logViewSDK.setText(log);
                Log.println(Log.INFO, "testesBruno", "setou esse texto no log sdk" + log);
                ((ScrollView) findViewById(R.id.scrollLog)).fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void checarBateria() {
        try {
            if(isFlightControllerAvailable()) {
                getProductInstance().getBattery().setStateCallback(new BatteryState.Callback() {
                    @Override
                    public void onUpdate(BatteryState djiBatteryState) {
                        nivelBateria = djiBatteryState.getChargeRemainingInPercent();
                    }
                });

                showToast(String.valueOf(nivelBateria) + '%');
            } else {
                showToast("Drone não conectado ou detectado!");
            }

        } catch (Exception ignored) {

        }
    }

    public void consultarSerialDrone(){
        if(getAircraftInstance() != null && getAircraftInstance().getFlightController()!=null) {
            getAircraftInstance().getFlightController().getSerialNumber(new CommonCallbacks.CompletionCallbackWith<String>() {
                @Override
                public void onSuccess(String s) {
                    serialNumber = s;
                    enviaSerialParaControleActivity(s);
                    enviarSerialParaMensageriaThread(s);
                    atualizarAcaoBotaoEnviar();
                }

                @Override
                public void onFailure(DJIError djiError) {
                    showToast("getSerialNumber failed: " + djiError.getDescription());
                    enviaSerialParaControleActivity(null);
                    enviarSerialParaMensageriaThread("xx");
                    serialNumber="xx";
                    atualizarAcaoBotaoEnviar();
                }
            });
        } else {
            showToast("Drone não conectado!");
            enviaSerialParaControleActivity(null);
            enviarSerialParaMensageriaThread("xx");
            serialNumber="xx";
            atualizarAcaoBotaoEnviar();

        }
    }

    public void atualizarViewSerial(String serial){
        if(serial != null) {
            ((TextView) findViewById(R.id.serialDrone)).setText(serialNumber);
            ((TextView) findViewById(R.id.serialDrone)).setTextColor(getResources().getColor(R.color.green));
        } else {
            ((TextView) findViewById(R.id.serialDrone)).setText("Desconectado!");
            ((TextView) findViewById(R.id.serialDrone)).setTextColor(getResources().getColor(R.color.red));
        }
        atualizarViewRollPitchMode();
    }

    public void atualizarViewRollPitchMode() {
        String rowPitchControlMode;
        if(isFlightControllerAvailable()){
            if(getAircraftInstance().getFlightController().getRollPitchControlMode().value()== RollPitchControlMode.ANGLE.value()){
                rowPitchControlMode = "ANGLE";
            } else {
                rowPitchControlMode = "VELOCITY";
            };
        } else {
            rowPitchControlMode = "";
        }
        ((TextView) findViewById(R.id.rollPitchControlMode)).setText(
                ((TextView) findViewById(R.id.rollPitchControlMode)).getText() + rowPitchControlMode);
    }


    /**
     * Usado para enviar intent de modificação do texto do serial do drone
     * @param message
     */
    private void enviaSerialParaControleActivity(String message){
        Intent intent = new Intent(ControleActivity.ACTION_SERIAL_DRONE);
        intent.removeExtra("serial");
        intent.putExtra ("serial", message);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Usado para atualizar o serial que deve servir para filtrar as mensagens
     * recebidas pela Thread responsável com a comunicação do rabbitMQ.
     * @param serial
     */
    private void enviarSerialParaMensageriaThread(String serial) {
        Message msg = new Message();
        Bundle bundleSample = new Bundle();
        bundleSample.putString("serialDrone", serial);
        msg.setData(bundleSample);
        if(mensageriaThread.mHandler != null) {
            mensageriaThread.mHandler.sendMessage(msg);
            showToast("enviou serial " + serial + "para thread mensageria.");
        } else {
            showToast("handler da thread mensageria nulo");
        }
    }

}