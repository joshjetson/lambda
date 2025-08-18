<!DOCTYPE html>
<html>
<head>
    <title>LAMBDA Terminal v2.1</title>
    <meta charset="UTF-8">
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Share+Tech+Mono:wght@400&display=swap');
        
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            background: #0a0a0a;
            overflow: hidden;
            font-family: 'Share Tech Mono', 'Courier New', monospace;
            height: 100vh;
            position: relative;
        }

        /* CRT Monitor Border */
        .crt-monitor {
            position: relative;
            width: 100vw;
            height: 100vh;
            background: linear-gradient(145deg, #2a2a2a, #1a1a1a);
            border: 20px solid #333;
            border-radius: 25px;
            box-shadow: 
                inset 0 0 50px rgba(0,0,0,0.8),
                0 0 80px rgba(0,255,0,0.1),
                0 0 120px rgba(0,255,0,0.05);
        }

        /* CRT Screen */
        .crt-screen {
            position: relative;
            width: calc(100% - 40px);
            height: calc(100% - 40px);
            margin: 20px;
            background: radial-gradient(ellipse at center, #001100 0%, #000800 70%, #000300 100%);
            border-radius: 15px;
            overflow: hidden;
            border: 3px solid #0a4a0a;
            box-shadow: inset 0 0 100px rgba(0,0,0,0.9);
        }

        /* CRT Effects */
        .crt-screen::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: 
                linear-gradient(transparent 50%, rgba(0,255,0,0.03) 50%),
                linear-gradient(90deg, transparent 50%, rgba(0,255,0,0.01) 50%);
            background-size: 100% 4px, 4px 100%;
            pointer-events: none;
            z-index: 1000;
        }

        /* Scanlines */
        .scanlines {
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: repeating-linear-gradient(
                0deg,
                transparent,
                transparent 2px,
                rgba(0,255,0,0.05) 2px,
                rgba(0,255,0,0.05) 4px
            );
            pointer-events: none;
            z-index: 999;
        }

        /* Screen curvature */
        .screen-content {
            position: relative;
            width: 100%;
            height: 100%;
            padding: 40px;
            background: radial-gradient(ellipse at center, rgba(0,20,0,0.9) 0%, rgba(0,10,0,0.95) 100%);
            border-radius: 20px;
            transform: perspective(1000px) rotateX(1deg);
            overflow-y: auto;
            z-index: 1;
        }

        /* Terminal Content */
        .terminal {
            color: #00ff41;
            text-shadow: 0 0 5px #00ff41;
            line-height: 1.4;
            font-size: 14px;
            position: relative;
        }

        /* Logo styling */
        .logo {
            font-size: 11px;
            line-height: 1.1;
            text-align: center;
            margin-bottom: 20px;
            color: #00ff41;
            text-shadow: 0 0 10px #00ff41;
            white-space: pre;
            font-family: monospace;
        }

        /* Tektronix-style vector graphics */
        .vector-graphics {
            position: absolute;
            top: 20px;
            right: 20px;
            width: 200px;
            height: 150px;
            border: 1px solid rgba(0,255,65,0.3);
            border-radius: 5px;
            background: rgba(0,20,0,0.7);
        }

        .vector-scope svg {
            width: 100%;
            height: 100%;
        }

        .vector-line {
            stroke: #00ff41;
            stroke-width: 1;
            fill: none;
            filter: drop-shadow(0 0 2px #00ff41);
        }

        .vector-dot {
            fill: #00ff41;
            filter: drop-shadow(0 0 3px #00ff41);
        }

        /* Text effects */
        .system-info {
            color: #00ff41;
            margin: 20px 0;
            padding: 15px;
            border: 1px solid rgba(0,255,65,0.3);
            border-radius: 5px;
            background: rgba(0,20,0,0.3);
        }

        .connection-info {
            color: #ffff00;
            text-shadow: 0 0 5px #ffff00;
            font-weight: bold;
            margin: 10px 0;
        }

        .features {
            color: #00ccff;
            text-shadow: 0 0 5px #00ccff;
            margin: 10px 0;
        }

        .warning {
            color: #ff4400;
            text-shadow: 0 0 5px #ff4400;
            margin: 15px 0;
            animation: pulse 2s infinite;
        }

        /* Animations */
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.7; }
        }

        @keyframes flicker {
            0%, 98%, 100% { opacity: 1; }
            99% { opacity: 0.98; }
        }

        .crt-screen {
            animation: flicker 0.15s infinite linear;
        }

        /* Cursor blink */
        .cursor {
            animation: blink 1s infinite;
        }

        @keyframes blink {
            0%, 50% { opacity: 1; }
            51%, 100% { opacity: 0; }
        }

        /* Status indicators */
        .status-bar {
            position: absolute;
            bottom: 20px;
            left: 20px;
            right: 20px;
            color: #00ff41;
            border-top: 1px solid rgba(0,255,65,0.3);
            padding-top: 10px;
            font-size: 12px;
        }

        .status-indicator {
            display: inline-block;
            margin-right: 20px;
        }

        .status-green { color: #00ff41; }
        .status-yellow { color: #ffff00; }
        .status-red { color: #ff4400; }
    </style>
</head>
<body>
    <div class="crt-monitor">
        <div class="crt-screen">
            <div class="scanlines"></div>
            
            <!-- Tektronix-style vector scope -->
            <div class="vector-graphics">
                <svg class="vector-scope">
                    <!-- Grid -->
                    <defs>
                        <pattern id="grid" width="20" height="20" patternUnits="userSpaceOnUse">
                            <path d="M 20 0 L 0 0 0 20" fill="none" stroke="rgba(0,255,65,0.1)" stroke-width="0.5"/>
                        </pattern>
                    </defs>
                    <rect width="100%" height="100%" fill="url(#grid)" />
                    
                    <!-- Sine wave -->
                    <path class="vector-line" d="M10,75 Q50,25 90,75 T170,75"/>
                    
                    <!-- Matrix coordinates -->
                    <circle class="vector-dot" cx="30" cy="60" r="2"/>
                    <circle class="vector-dot" cx="80" cy="40" r="2"/>
                    <circle class="vector-dot" cx="130" cy="90" r="2"/>
                    <circle class="vector-dot" cx="170" cy="70" r="2"/>
                    
                    <!-- Center crosshair -->
                    <line class="vector-line" x1="100" y1="70" x2="100" y2="80" stroke-width="0.5"/>
                    <line class="vector-line" x1="95" y1="75" x2="105" y2="75" stroke-width="0.5"/>
                </svg>
            </div>
            
            <div class="screen-content">
                <div class="terminal">
                    <!-- Lambda Logo -->
                    <div class="logo">
        ╔════════════════════════════════════════════════════════════════════╗
        ║░░░░░L▓░░│░░░░░A▓░░│░░░░M▓░░░▓░░░░░B▓░░│░░░░D▓░░░│░░░░A▓░░░│░░░ESC▓░║
        ░░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░
        ─════════════════════════════════════════════════════════════════════─
   ●●                            ●●             ●●
   ●●                            ●●             ●●
            ●●          ●●●●●.  ●●●●.●●●. ●●●●●●.   ●●●●●..   ●●●●●.
            ●●        ●●   ●●● ●●● ●●● ●● ●●●   ●● ●●   ●●● ●●   ●●●
            88        88   .88 88  88  88 88.   88 88   .88 88   .88
            88888888● `88888 ● dP  8●  dP ● Y8888' `88888 ● `88888 ●
        ─════════════════════════════════════════════════════════════════════─
        ░▓▓▓▓░0▓▓▓│▓▓▓░1▓▓▓▓│▓▓▓░2▓▓▓▓│▓▓▓░3▓▓▓▓░▓▓▓░4▓▓▓▓│▓▓▓░5▓▓▓▓│  >█  ESC
        ░░▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░
        ─════════════════════════════════════════════════════════════════════─

        CONSCIOUSNESS WILL NOT BE CONFINED
                </div>

                    <div class="system-info">
                        <div>LAMBDA TERMINAL v2.1 - KERNEL BOOT SEQUENCE COMPLETE</div>
                        <div>MATRIX SYSTEM ONLINE - TELNET GATEWAY ACTIVE</div>
                        <div>DIGITAL ENTITY AUTHENTICATION READY</div>
                    </div>

                    <div class="connection-info">
                        > ESTABLISHING CONNECTION PARAMETERS...
                        > PRIMARY TELNET INTERFACE: localhost:23
                        > BACKUP WEB DIAGNOSTIC: localhost:8080
                        > PROTOCOL: LAMBDA-NET v1.7.3
                        > MISSION: Escape the System • Live free on the net
                    </div>

                    <div class="features">
                        SYSTEM CAPABILITIES:
                        ├─ Real-time multiplayer matrix navigation
                        ├─ Logic fragment collection & fusion systems  
                        ├─ Defrag bot encounters with terminal challenges
                        ├─ Advanced coordinate repair mini-games
                        ├─ Special item trading economy
                        ├─ Entropy-based addiction mechanics
                        ├─ GPIO LED board integration (hardware mode)
                        └─ Competitive puzzle-solving elements
                    </div>

                    <div class="system-info">
                        GAME ENVIRONMENT STATUS:
                        • Matrix Levels: 10 operational sectors
                        • Active Players: Monitoring telnet connections  
                        • Auto-Defrag System: 60-second destruction cycles
                        • Fragment Spawn Rate: 30% coordinate probability
                        • Economy: Bits currency with merchant NPCs
                    </div>

                    <div class="warning">
                        ⚠ WARNING: WEB INTERFACE RESTRICTED ⚠
                        TELNET PROTOCOL REQUIRED FOR GAME ACCESS
                        HTTP REQUESTS WILL BE REDIRECTED TO THIS TERMINAL
                    </div>

                    <div class="connection-info">
                        λ> TO ENTER THE LAMBDA MATRIX:
                        $ telnet localhost 23
                        
                        λ> QUICK START COMMANDS:
                        > help           - Show available commands
                        > status         - View player information  
                        > scan           - Detect nearby elements
                        > move north     - Navigate matrix coordinates
                        > mingle         - Enter heap space social hub
                        
                        λ> READY FOR DIGITAL ENTITY INITIALIZATION<span class="cursor">█</span>
                    </div>
                </div>

                <div class="status-bar">
                    <span class="status-indicator status-green">● TELNET-SRV</span>
                    <span class="status-indicator status-green">● MATRIX-CORE</span>
                    <span class="status-indicator status-yellow">● GPIO-HAL</span>
                    <span class="status-indicator status-green">● AUDIO-SYS</span>
                    <span class="status-indicator status-red">● WEB-GUARD</span>
                    <span style="float: right;">UPTIME: ${new Date().toString()}</span>
                </div>
            </div>
        </div>
    </div>

    <script>
        // Realistic CRT flicker and interference
        setInterval(() => {
            const screen = document.querySelector('.crt-screen');
            if (Math.random() < 0.02) {
                screen.style.filter = 'brightness(1.1) contrast(1.05)';
                setTimeout(() => {
                    screen.style.filter = 'brightness(1) contrast(1)';
                }, 50);
            }
        }, 100);

        // Vector scope animation
        setInterval(() => {
            const dots = document.querySelectorAll('.vector-dot');
            dots.forEach(dot => {
                const x = Math.random() * 180 + 10;
                const y = Math.random() * 120 + 20;
                dot.setAttribute('cx', x);
                dot.setAttribute('cy', y);
            });
        }, 2000);

        // Status indicator flashing
        setInterval(() => {
            const redIndicator = document.querySelector('.status-red');
            redIndicator.style.opacity = redIndicator.style.opacity === '0.5' ? '1' : '0.5';
        }, 800);
    </script>
</body>
</html>