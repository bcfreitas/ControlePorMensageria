# ControlePorMensageria

______________________
| client              |
| (pc, mobile, etc)   |
|_____________________|
          |
          |   HTTP (REST request)
          |
__________V____________
|   rabbitMQ server   |
|_____________________|
          /\
          |  AMQP (open channel)
          |
__________V___________________
|  app ControlePorMensaeria  |
|____________________________|
          |
          |  USB
          V
__________________________
|  controle remoto Drone |
|________________________|
          |
          |  WiFI otimizado DJI
          V
_____________________
|   rone Mavic Mini |
|___________________|


Este app serve como receptor de comandos de navegação de meios externos pela internet (inicialmente idealizado um software em um servidor que terá a inteligência de navegação do drone), e tradutor dos comandos para enviar ao controle remoto do DRONE por meio da SDK DJI, para que o controle se comunique com o DRONE enviando o comando apropriado de movimentação.
Contudo, até então a SDK ainda não foi importada no projeto. O app só puxa os comandos que foram enviados do servidor rabbitMQ e simula a tradução do comando animando um conjunto de setas apresentadas na tela do dispositivo.

A implementação da comunicação com o servidor rabbitMQ (que pode ser aproveitada para outros fins), está toda na classe MensageriaThread.java.

INSTALAÇÃO: 

(Forma 1) Copiar o APK localizado em /app/build/outputs/apk/debug/app-debug.apk para seu dispositivo móvel com Android e instalar;
Obs.: é necessário que a instalação de fontes externas esteja habilitada na configuração do Android. Ele dará um aviso caso não esteja.

(Forma 2) Usar o Android Studio em um PC com o celular android conectado por USB, e executar Run / Run APP.
Obs.: é necessário habilitar o modo depuração nas configurações do android. Ele dará um aviso caso não esteja.





