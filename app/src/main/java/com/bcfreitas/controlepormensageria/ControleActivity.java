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
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import dji.common.error.DJIError;
import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class ControleActivity extends AppCompatActivity {

    Handler handler = new Handler();
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

    private float pitch;
    private float roll;
    private float yaw;
    private float throttle;


    private BroadcastReceiver mensageria = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getExtras().getString("comando")!=null) {
                String comando = intent.getExtras().getString("comando");
                if (comando.equals("a")) {
                    recebeComandoNavegacao(R.id.botao_esquerda);
                } else if (comando.equals("d")) {
                    recebeComandoNavegacao(R.id.botao_direita);
                } else if (comando.equals("w")) {
                    recebeComandoNavegacao(R.id.botao_frente);
                } else if (comando.equals("s")) {
                    recebeComandoNavegacao(R.id.botao_tras);
                } else {
                    Toast.makeText(getApplicationContext(), comando, Toast.LENGTH_SHORT).show();
                    //Registra log do comando recebido na TextView da tela
                    TextView logView = findViewById(R.id.logComandos);
                }
            }
        }
    };

    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver((mensageria),
                new IntentFilter("comandoIntent")
        );
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(mensageria);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controle);
        estados.add(R.id.botao_frente);
        estados.add(R.id.botao_centro);
        estados.add(R.id.botao_tras);
        estados.add(R.id.botao_esquerda);
        estados.add(R.id.botao_direita);

        MensageriaThread mensageriaThread = new MensageriaThread(getApplicationContext());
        mensageriaThread.start();

        findViewById(R.id.btnSendMessage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(getApplicationContext(), "Enviando mensagem 'a' para o servidor rabbitMQ.", Toast.LENGTH_SHORT).show();
                Message msg2 = new Message();
                Bundle bundleSample = new Bundle();
                bundleSample.putString("msgParaRabbitMQ","a");
                msg2.setData(bundleSample);
                mensageriaThread.mHandler.sendMessage(msg2);
            }
        });
    }

    //adiciona log do comando à TextView da tela, para consulta.
    private void registraLog(TextView logView, int direcao) {
        String textoDirecao;
        switch (direcao){
            case R.id.botao_direita:
                textoDirecao = "direita";
                break;
            case R.id.botao_esquerda:
                textoDirecao = "esquerda";
                break;
            case R.id.botao_frente:
                textoDirecao = "para frente";
                break;
            case R.id.botao_tras:
                textoDirecao = "para trás";
                break;
            case R.id.botao_centro:
                textoDirecao = "pare!";
                break;
            default:
                textoDirecao = "";
        }
        String log = (String) logView.getText();
        log = log + "\n" + (new Date().toString()) + " - " + textoDirecao;
        logView.setText(log);
        ((ScrollView) findViewById(R.id.scrollLog)).fullScroll(View.FOCUS_DOWN);
    }

    public void recebeComandoNavegacao(int direcao) {
        //Executa ação/comando
        executaComando(direcao);
        this.ultimoComando = direcao;
        dataUltimoComando = new Date();

        //Registra log do comando recebido na TextView da tela
        TextView logView = findViewById(R.id.logComandos);
        registraLog(logView, direcao);

        //Cria thread para voltar ao estado parado após o intervalo de tempo definido, se
        //o comando for diferente do estado inicial
        if(direcao!=R.id.botao_centro) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    retornaEstadoParado();
                }
            }, tempoPadraoComando);
        }

        //TODO: traduzir comandos!
        //por enquanto é só um teste inicial pra girar o drone
        enviaComandoParaDrone();
    }

    public void enviaComandoParaDrone(){
        //Coordenadas X e Y do controle virtual anaĺogico esquerdo (girar, subir/descer - eixo Z )
        float controleEsquerdo_pX = 0;
        float controleEsquerdo_pY = 0;
        //Coordenadas X e Y do controle virtual anaĺogico direito (frente, trás, esquerda,
        //  direita - eixos X/Y)
        float controleDireito_pX = 0;
        float controleDireito_pY = 0;

        //TODO - Precisamos rever como os dados serão enviados pela mensageria.
        //Por enquanto, apenas para fins de teste básico
        //um movimento de GIRO para esquerda (o stick esquerdo controla isso).
        controleEsquerdo_pX = -0.3f;

        //TODO - entender o impacto destes valores na prática
        float verticalJoyControlMaxSpeed = 2;
        float yawJoyControlMaxSpeed = 3;

        float pitchJoyControlMaxSpeed = 10;
        float rollJoyControlMaxSpeed = 10;

        //flag setada como true por padrão, fazendo com que
        // pitch: represente posição do eixo X do virtual stick
        // roll: represente posição do eixo Y do virtual stick
        if (horizontalCoordinateFlag) {
            if (rollPitchControlModeFlag) {
                pitch = (float) (pitchJoyControlMaxSpeed * controleDireito_pX);
                roll = (float) (rollJoyControlMaxSpeed * controleDireito_pY);
            } else {
                pitch = -(float) (pitchJoyControlMaxSpeed * controleDireito_pY);
                roll = (float) (rollJoyControlMaxSpeed * controleDireito_pX);
            }
        }

        //yaw representa o giro do drone, tem dois modos:
        // modo velocidade angular, em que o valor passado é a quantidade de graus por segundo,
        // modo ângulo, em que o valor passado é o ângulo para girar.
        yaw = yawJoyControlMaxSpeed * controleEsquerdo_pX;

        //throtle representa o movimento vertical (eixo Z), tem dois modos:
        // modo posição: valor que representa a altitude em relação à posição de decolagem,
        // modo velocidade: valores positivos representam ascenção, negativos descida.
        throttle = verticalJoyControlMaxSpeed * controleEsquerdo_pY;

        //Aqui é criada a classe que vai ser agendada para executar e enviar uma instância de
        // FlightControlData com os dados de navegação pitch, roll, yaw e throttle atribuídos acima.
        if (null == sendVirtualStickDataTimer) {
            sendVirtualStickDataTask = new SendVirtualStickDataTask();
            sendVirtualStickDataTimer = new Timer();
            sendVirtualStickDataTimer.schedule(sendVirtualStickDataTask, 0, 200);
        }
    }

    private class SendVirtualStickDataTask extends TimerTask {

        @Override
        public void run() {
            if (isFlightControllerAvailable()) {
                getAircraftInstance()
                        .getFlightController()
                        .sendVirtualStickFlightControlData(new FlightControlData(pitch,
                                        roll,
                                        yaw,
                                        throttle),
                                new CommonCallbacks.CompletionCallback() {
                                    @Override
                                    public void onResult(DJIError djiError) {

                                    }
                                });
            } else {
                showToast("Drone não está conectado!!!");
            }
        }
    }

    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static boolean isFlightControllerAvailable() {
        return isProductModuleAvailable() && isAircraft() && (null != getAircraftInstance()
                .getFlightController());
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

}