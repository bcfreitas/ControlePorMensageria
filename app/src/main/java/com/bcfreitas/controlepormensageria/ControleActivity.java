package com.bcfreitas.controlepormensageria;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

public class ControleActivity extends AppCompatActivity {

    Handler handler = new Handler();
    int tempoParaConsulta = 2000;
    boolean sincronizacaoAtiva;
    Collection<Integer> estados = new ArrayList<Integer>();
    Integer ultimoComando;
    Date dataUltimoComando;
    Integer tempoPadraoComando = 1000;
    private MensageriaThread mensageriaThread;


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