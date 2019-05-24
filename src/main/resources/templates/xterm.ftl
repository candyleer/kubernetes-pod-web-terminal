<!doctype html>
<html lang="zh">
<head>
    <link href="css/xterm.min.css" rel="stylesheet">
    <link href="css/fullscreen.min.css" rel="stylesheet">
    <title>web terminal</title>
</head>
<body>
<div id="terminal" style="overflow: hidden;">
    <div id="error" ></div>
</div>
<script src="js/xterm/reconnecting-websocket.js"></script>
<script src="js/xterm/xterm.min.js"></script>
<script src="js/xterm/attach.min.js"></script>
<script src="js/xterm/fit.js"></script>
<script src="js/xterm/fullscreen.min.js"></script>
<script>
    Terminal.applyAddon(fit);
    Terminal.applyAddon(attach);
    var cols;
    var rows;
    // Terminal.applyAddon(fullscreen);
    var term = new Terminal({
        // fontFamily   : 'Hack Braille, Courier New, Courier, monospace',
        cursorBlink: true,
        // fontSize: 20,
        theme: {
            foreground: '#d2d2d2',
            background: '#2b2b2b',
            cursor: '#adadad',
            black: '#000000',
            red: '#d81e00',
            green: '#5ea702',
            yellow: '#cfae00',
            blue: '#427ab3',
            magenta: '#89658e',
            cyan: '#00a7aa',
            white: '#dbded8',
            brightBlack: '#686a66',
            brightRed: '#f54235',
            brightGreen: '#99e343',
            brightYellow: '#fdeb61',
            brightBlue: '#84b0d8',
            brightMagenta: '#bc94b7',
            brightCyan: '#37e6e8',
            brightWhite: '#f1f1f0',
        },
    });
    console.log('init...');
    var sock = new window.WebSocket(
        'ws://' + location.host + '/terminal?namespace=${namespace}&podName=${podName}');
    sock.reconnectAttempts = 3;
    var terminal = document.getElementById('terminal');
    sock.onopen = function () {
        console.log('open');
        term.writable = true;
        term.isTTY = true;

        term.open(terminal, true);
        // app.onTerminalInit();
        // app.onTerminalReady();

        // term.toggleFullScreen(true);
        term.fit();
        cols = term.cols;
        rows = term.rows;
        sock.send('___init___' + cols + '___' + rows);
        term.focus();
        console.log(cols, rows);

        term.attach(sock);
        window.onresize = function () {
            term.fit();
        };
        term.on('resize', function (data) {
            cols = data.cols;
            rows = data.rows;
            console.log('resize', cols, rows);
            sock.send('___resize___' + cols + '___' + rows);
        });
        setInterval(function () {
            sock.send('___resize___' + cols + '___' + rows);
        }, 30000);
    };
    sock.onerror = function (e, m) {
        document.getElementById("error").innerHTML="无权限访问！";
    };
</script>
<#--<script src="js/main.js"></script>-->
</body>
</html>