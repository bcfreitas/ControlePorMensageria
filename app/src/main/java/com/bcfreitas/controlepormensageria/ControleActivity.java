package com.bcfreitas.controlepormensageria;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import dji.common.battery.BatteryState;
import dji.common.camera.SettingsDefinitions;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightOrientationMode;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;
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
    public String urlRabbitmq;

    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;

    public int DURACAO_PADRAO_EM_DECISEGUNDOS = 5;
    public static final int INTERVALO_DE_ENVIO_PADRAO = 10;
    public int VALOR_PADRAO = 1;
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


                Integer valorExplicito = VALOR_PADRAO; //valor padrao = 1
                String acao;
                Integer duracaoEmDecisegundos = DURACAO_PADRAO_EM_DECISEGUNDOS; //tempo padrao = 0.5s

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
                } else if (acao.equals("ge")) {
                    recebeComandoNavegacao(R.id.botao_girar_esquerda, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("gd")) {
                    recebeComandoNavegacao(R.id.botao_girar_direita, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("on")) {
                    recebeComandoNavegacao(R.id.botao_on, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("off")) {
                    recebeComandoNavegacao(R.id.botao_off, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("bat")) {
                    recebeComandoNavegacao(R.id.botao_bateria, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("up")) {
                    recebeComandoNavegacao(R.id.botao_up, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("down")) {
                    recebeComandoNavegacao(R.id.botao_down, duracaoEmDecisegundos, valorExplicito);
                } else if (acao.equals("ph")) {
                    recebeComandoNavegacao(R.id.botao_photo, duracaoEmDecisegundos, valorExplicito);
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
        urlRabbitmq = (String) intent.getExtras().get("urlRabbitmq");

        setContentView(R.layout.activity_controle);
        estados.add(R.id.botao_frente);
        estados.add(R.id.botao_centro);
        estados.add(R.id.botao_tras);
        estados.add(R.id.botao_esquerda);
        estados.add(R.id.botao_direita);
        estados.add(R.id.botao_takeoff);
        estados.add(R.id.botao_girar_esquerda);
        estados.add(R.id.botao_girar_direita);
        estados.add(R.id.botao_land);
        estados.add(R.id.botao_on);
        estados.add(R.id.botao_off);
        estados.add(R.id.botao_bateria);
        estados.add(R.id.botao_up);
        estados.add(R.id.botao_up2);
        estados.add(R.id.botao_down);
        estados.add(R.id.botao_down2);
        estados.add(R.id.botao_photo);

        mensageriaThread = MensageriaThread.getInstance();
        mensageriaThread.setBroadcaster(getApplicationContext());
        mensageriaThread.setFilaParaMensageria(filaParaMensageria);
        mensageriaThread.setUrlRabbitmq(urlRabbitmq);
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

        findViewById(R.id.botao_girar_esquerda).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_girar_esquerda, 3, 1);
            }
        });

        findViewById(R.id.botao_girar_direita).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_girar_direita, 3, 1);
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
                recebeComandoNavegacao(R.id.botao_centro, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });
        findViewById(R.id.botao_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_up, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });
        findViewById(R.id.botao_up2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_up2, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });
        findViewById(R.id.botao_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_down, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });
        findViewById(R.id.botao_down2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_down2, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
            }
        });
        findViewById(R.id.botao_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recebeComandoNavegacao(R.id.botao_photo, DURACAO_PADRAO_EM_DECISEGUNDOS, VALOR_PADRAO);
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
                        VALOR_PADRAO = 3;
                        DURACAO_PADRAO_EM_DECISEGUNDOS=5;
                    } else {
                        getAircraftInstance().getFlightController().setRollPitchControlMode(RollPitchControlMode.VELOCITY);
                        //Altera o valor padrão para 1 por segurança, pois o valor padrão de 5 quando usa velocidade se torna 5m/s,
                        VALOR_PADRAO = 1;
                        DURACAO_PADRAO_EM_DECISEGUNDOS = 3;
                    }
                } else {
                    showToast("Drone não está conectado!");
                    ((Spinner) findViewById(R.id.rollPitchModeSelect)).setSelection(0, true);
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner flightCoordinateModeSpinner = ((Spinner) findViewById(R.id.flightCoordinateSelect));
        flightCoordinateModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id ) {
                if(isFlightControllerAvailable()){
                    configuraFlightCoordinateSystem();
                } else {
                    showToast("Drone não está conectado!");
                    ((Spinner) findViewById(R.id.flightCoordinateSelect)).setSelection(0, true);

                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner yawControlModeSpinner = ((Spinner) findViewById(R.id.yawControlModeSelect));
        yawControlModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id ) {
                if(isFlightControllerAvailable()){
                    configuraYawControlMode();
                } else {
                    showToast("Drone não está conectado!");
                    ((Spinner) findViewById(R.id.yawControlModeSelect)).setSelection(0, true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner verticalThrottleModeSpinner = ((Spinner) findViewById(R.id.verticalThrottleModeSelect));
        verticalThrottleModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id ) {
                if(isFlightControllerAvailable()){
                    configuraVerticalThrottleMode();
                } else {
                    showToast("Drone não está conectado!");
                    ((Spinner) findViewById(R.id.verticalThrottleModeSelect)).setSelection(0, true);

                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    public void configuraFlightCoordinateSystem(){
        String flightCooordinateModeSpinnerValue;
        flightCooordinateModeSpinnerValue = (String) ((Spinner) findViewById(R.id.flightCoordinateSelect)).getSelectedItem();
        if(isFlightControllerAvailable()) {
            if (flightCooordinateModeSpinnerValue.equals("GROUND")) {
                getAircraftInstance().getFlightController().setRollPitchCoordinateSystem(FlightCoordinateSystem.GROUND);
                Log.println(Log.INFO, "testesBruno", "setou flight coordinate GROUND");
            } else {
                getAircraftInstance().getFlightController().setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
                Log.println(Log.INFO, "testesBruno", "setou flight coordinate BODY");
            }
        }
    }

    public void configuraYawControlMode(){
        String yawControlModeSpinnerValue;
        yawControlModeSpinnerValue = (String) ((Spinner) findViewById(R.id.yawControlModeSelect)).getSelectedItem();
        if(isFlightControllerAvailable()) {
            if (yawControlModeSpinnerValue.equals("ANGLE")) {
                getAircraftInstance().getFlightController().setYawControlMode(YawControlMode.ANGLE);
                Log.println(Log.INFO, "testesBruno", "setou yaw mode ANGLE");
            } else {
                getAircraftInstance().getFlightController().setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
                Log.println(Log.INFO, "testesBruno", "setou yaw mode BODY");
            }
        }
    }

    public void configuraVerticalThrottleMode(){
        String verticalThrottleMode;
        verticalThrottleMode = (String) ((Spinner) findViewById(R.id.verticalThrottleModeSelect)).getSelectedItem();
        if(isFlightControllerAvailable()) {
            if (verticalThrottleMode.equals("VELOCITY")) {
                getAircraftInstance().getFlightController().setVerticalControlMode(VerticalControlMode.VELOCITY);
                Log.println(Log.INFO, "testesBruno", "setou verticalControlMode Velocity");
            } else if (verticalThrottleMode.equals("POSITION")) {
                getAircraftInstance().getFlightController().setVerticalControlMode(VerticalControlMode.POSITION);
                Log.println(Log.INFO, "testesBruno", "setou verticalControlMode POSITION");
            }
        }
    }

    private void toggleVirtualStick(boolean b, String s) {
        getAircraftInstance().getFlightController().setVirtualStickModeEnabled(b, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
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
            case R.id.botao_girar_esquerda:
                textoDirecao = valorExplicito + " girar  esquerda ";
                break;
            case R.id.botao_girar_direita:
                textoDirecao = valorExplicito + " girar  direita ";
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
            case R.id.botao_up:
            case R.id.botao_up2:
                textoDirecao = valorExplicito + " para cima por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_down:
            case R.id.botao_down2:
                textoDirecao = valorExplicito + " para baixo por " + duracaoEmDecisegundos*100 + "ms";
                break;
            case R.id.botao_photo:
                textoDirecao = "tirar foto!";
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

        //verifica altitude atual e o ângulo de inclinação da face do drone em relação ao norte
        //para passar como parâmetros e evitar movimentos de giro ou elevação indevidos no caso de outros movimentos.
        if(isFlightControllerAvailable()){
            //throttle = getAircraftInstance().getFlightController().getState().getAircraftLocation().getAltitude();
            throttle = 0;
            yaw = getAircraftInstance().getFlightController().getCompass().getHeading();
        } else {
            throttle = 0;
            yaw = 0;
        }

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
            case R.id.botao_up:
            case R.id.botao_up2:
                throttle = 1; //valorExplicito;
                if(isFlightControllerAvailable()){
                    throttle = getAircraftInstance().getFlightController().getState().getAircraftLocation().getAltitude()+1;
                }
                break;
            case R.id.botao_down:
            case R.id.botao_down2:
                throttle = 0; //-1*valorExplicito;
                if(isFlightControllerAvailable()){
                    float altitudeAtual = getAircraftInstance().getFlightController().getState().getAircraftLocation().getAltitude();
                    if(altitudeAtual >=1) {
                        throttle = altitudeAtual-1;
                    }
                }
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
            case R.id.botao_girar_esquerda:
                //giro fixo de 45 graus
                yaw = yaw + 45;
                break;
            case R.id.botao_girar_direita:
                //giro fixo de 45 graus
                yaw = yaw - 45;
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
            case R.id.botao_photo:
                captureAction();
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

        //reforça modo de coordenada que deve ser usado para o comando.
        configuraFlightCoordinateSystem();

        //Aqui é criada a classe que vai ser agendada para executar e enviar uma instância de
        // FlightControlData com os dados de navegação pitch, roll, yaw e throttle atribuídos acima.
        if (isFlightControllerAvailable()) {
            toggleVirtualStick(true, "VirtualStick ativado");
            sendVirtualStickDataTask = new SendVirtualStickDataTask();
            sendVirtualStickDataTimer = new Timer();
            sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 100, 100); //100ms permite 10 vezes/s = 10Hz
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sendVirtualStickDataTimer.cancel();
                    toggleVirtualStick(false, "");
                    enviarPosicaoDroneParaFiware();
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
                                        SendConfirmLanding sendConfirmLanding = new SendConfirmLanding();
                                        sendVirtualStickDataTimer = new Timer();
                                        handler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                sendVirtualStickDataTimer.schedule(sendConfirmLanding, new Date());
                                                enviarPosicaoDroneParaFiware();
                                            }
                                        }, 1000);

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

    private class SendConfirmLanding extends TimerTask {

        @Override
        public void run() {
            try {
                if (isFlightControllerAvailable()) {
                    getAircraftInstance()
                            .getFlightController().confirmLanding(
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    if(djiError!=null) {
                                        showToast((djiError.getDescription()) + String.valueOf(djiError.getErrorCode()));
                                    }
                                }
                            });
                } else {
                    showToast("Drone não está conectado!!!");
                }
                registraLogSDK(" confirmLanding", null);
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
                    posicionaCameraParaBaixo();
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
        atualizarViewYawControlMode();
        atualizarViewCoordinateSystemMode();
        atualizarViewFlightOrientationMode();
        enviarPosicaoDroneParaFiware();
    }

    public void atualizarViewRollPitchMode() {
        int rollPitchControlMode;
        if(isFlightControllerAvailable()){
            if(getAircraftInstance().getFlightController().getRollPitchControlMode().value()== RollPitchControlMode.ANGLE.value()){
                rollPitchControlMode = 1;
            } else {
                rollPitchControlMode = 2;
            };
        } else {
            rollPitchControlMode = 0;
        }
        ((Spinner) findViewById(R.id.rollPitchModeSelect)).setSelection(rollPitchControlMode, true);
    }

    public void atualizarViewYawControlMode() {
        int yawControlMode;
        if(isFlightControllerAvailable()){
            if(getAircraftInstance().getFlightController().getYawControlMode().value()== YawControlMode.ANGLE.value()){
                yawControlMode = 1;
            } else {
                yawControlMode = 2;
            };
        } else {
            yawControlMode = 0;
        }
        ((Spinner) findViewById(R.id.yawControlModeSelect)).setSelection(yawControlMode, true);
    }

    public void atualizarViewCoordinateSystemMode() {
        int flightCoordinateSystem;
        if(isFlightControllerAvailable()){
            if(getAircraftInstance().getFlightController().getRollPitchCoordinateSystem().value()== FlightCoordinateSystem.GROUND.value()){
                flightCoordinateSystem = 1;
            } else {
                flightCoordinateSystem = 2;
            };
        } else {
            flightCoordinateSystem = 0;
        }

        ((Spinner) findViewById(R.id.flightCoordinateSelect)).setSelection(flightCoordinateSystem, true);
    }

    public void atualizarViewFlightOrientationMode() {
        int flightOrientationMode;
        if(isFlightControllerAvailable()){
            if(getAircraftInstance().getFlightController().getState().getOrientationMode().value()== FlightOrientationMode.AIRCRAFT_HEADING.value()){
                flightOrientationMode = 1;
            } else if(getAircraftInstance().getFlightController().getState().getOrientationMode().value()== FlightOrientationMode.COURSE_LOCK.value()) {
                flightOrientationMode = 2;
            } else {
                flightOrientationMode = 3;
            };
        } else {
            flightOrientationMode = 0;
        }

        ((Spinner) findViewById(R.id.verticalThrottleModeSelect)).setSelection(flightOrientationMode, true);
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
        } else {
            showToast("handler da thread mensageria nulo");
        }
    }

    public void enviarPosicaoDroneParaFiware(){
        if(isFlightControllerAvailable()){
            double latitude = getAircraftInstance().getFlightController().getState().getAircraftLocation().getLatitude();
            double longitude = getAircraftInstance().getFlightController().getState().getAircraftLocation().getLongitude();
            double altitude = getAircraftInstance().getFlightController().getState().getAircraftLocation().getAltitude();

            enviarDadosPorMensageria("fiwareServerData", "latitude:"+latitude+";longitude:"+longitude+";altitude:"+altitude);
        } else {
            enviarDadosPorMensageria("fiwareServerData", "drone desconectado");
        }
    }

    public void enviarDadosPorMensageria(String queueName, String data) {
        Message msg = new Message();
        Bundle bundleSample = new Bundle();
        bundleSample.putString(queueName, data);
        msg.setData(bundleSample);
        if(mensageriaThread.mHandler != null) {
            mensageriaThread.mHandler.sendMessage(msg);
        } else {
            showToast("handler da thread mensageria nulo");
        }
    }

    private void captureAction(){

        if(!isFlightControllerAvailable()){
            showToast("Drone desconectado.");
            return;
        }

        final Camera camera = getAircraftInstance().getCamera();

        camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO,
                        new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {

                            }
                        });

        camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error != null) {
                    showToast(error.getDescription());
                }
            }
        });

        camera.startShootPhoto(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    showToast("take photo: success");
                } else {
                    showToast(error.getDescription());
                }

                fetchMediaList();
            }
        });
    }

    private void fetchMediaList() {

        if(!isFlightControllerAvailable() || getAircraftInstance().getCamera() == null  || getAircraftInstance().getCamera().getMediaManager() == null ){
            showToast("Drone não conectado ou câmera não disponível (cartão SD inserido?).");
            return;
        }

        final MediaManager mediaManager = getAircraftInstance().getCamera().getMediaManager();
        mediaManager.refreshFileListOfStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, new CommonCallbacks.CompletionCallback() {

            @Override
            public void onResult(DJIError djiError) {
                String str;
                MediaFile mediaFile;

                List<MediaFile> djiMedias = mediaManager.getSDCardFileListSnapshot();
                if (null == djiError) {
                    mediaManager.refreshFileList(new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                        }
                    });


                   Collections.sort(djiMedias, new Comparator<MediaFile>(){
                        @Override
                        public int compare(MediaFile o1, MediaFile o2) {
                            return o1.getFileName().compareTo(o2.getFileName());
                        };
                    });

                    if (null != djiMedias) {
                        if (!djiMedias.isEmpty()) {
                            mediaFile = djiMedias.get(djiMedias.size()-1);

                            showToast ("Total Media files:" + djiMedias.size() + "\n" + ". Ultima foto: " +
                                    mediaFile.getFileName());

                            String nomeArquivo = mediaFile.getFileName();

                            mediaFile.fetchThumbnail(new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError djiError) {
                                    converterFotoEEnviar(mediaFile.getThumbnail(), nomeArquivo);
                                }


                            });

                        } else {
                            showToast("No Media in SD Card");
                        }
                    }
                } else {
                    showToast(djiError.getDescription());
                }
            }
        });
    }

    public void converterFotoEEnviar(Bitmap foto, String nomeArquivo){

        ByteArrayOutputStream bao = new ByteArrayOutputStream();

        foto.compress(Bitmap.CompressFormat.JPEG, 90, bao);

        byte[] photoData = bao.toByteArray();

        String imageEncoded = Base64.encodeToString(photoData,Base64.DEFAULT);

        String dadosImagem = "nomeArquivo: " + nomeArquivo + "; imagem_base64: " + imageEncoded;

        enviarDadosPorMensageria("fiwareServerData", dadosImagem);

    }

    public void posicionaCameraParaBaixo(){
        if(!isFlightControllerAvailable()){
            showToast("Drone desconectado. Impossível virar câmera para baixo.");
            return;
        }
        getAircraftInstance().getGimbal().
                rotate(new Rotation.Builder().pitch(-90)
                        .mode(RotationMode.ABSOLUTE_ANGLE)
                        .yaw(Rotation.NO_ROTATION)
                        .roll(Rotation.NO_ROTATION)
                        .time(2)
                        .build(), new CommonCallbacks.CompletionCallback() {

                    @Override
                    public void onResult(DJIError error) {

                    }
                });
    }
}