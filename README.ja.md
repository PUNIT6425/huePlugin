# huePlugin

## 概要

Kadecot の Plugin 仕様が公開されたのでサンプルをいじってHUEを
コントロールできるようにしてみました。

https://github.com/SonyCSL/SamplePlugin


機器発見のライブラリは無かったのですが、同じSSDPということで
Camera Remote API beta SDK のサンプルを利用しています。

https://developer.sony.com/ja/camera-remote-api-beta-sdk-page


## 送信メッセージの仕様

### procedure

procedureはgetとsetの2種類だけです。

#### Get
- com.sonycsl.kadecot.hue.procedure.lights.state.get

#### Set
- com.sonycsl.kadecot.hue.procedure.lights.state.set


### Arguments

"[]" 固定。


### ArgumentsKw

HUE Web API で送信するjsonと同じものを設定します、
jsonを送らない場合は{}を設定します。


### 実行例

- [48,6,{"deviceId":1},"com.sonycsl.kadecot.hue.procedure.lights.state.get",[],{}]

- [48,6,{"deviceId":1},"com.sonycsl.kadecot.hue.procedure.lights.state.set",[],{"on":true,"sat":255,"bri":0,"hue":46920}]



## 受信メッセージの仕様

### ArgumentsKw

HUE Web API のレスポンスで返るjsonと同じものが設定されます。

### 実行例

- [50,6,{},[],{"state": {
    "on": true,"bri": 1,"hue": 45000,"sat": 254,"xy": [0.1907,0.0868],"ct": 500,"alert": "none","effect": "none","colormode": "hs","reachable": true},
    "type": "Extended color light","name": "Hue Lamp 1","modelid": "LCT001","swversion": "66010820",
    "pointsymbol": {"1": "none","2": "none","3": "none","4": "none","5": "none","6": "none","7": "none","8": "none"} }

- [50,6,{},[],{"result":[{"success":{"\/lights\/1\/state\/on": true}},{"success":{"\/lights\/1\/state\/hue": 46920}},
    {"success":{"\/lights\/1\/state\/sat": 254}},{"success":{"\/lights\/1\/state\/bri": 0}}]}




