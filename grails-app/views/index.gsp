<!DOCTYPE html>
<html>
<head>
    <title>Lambda Game Server</title>
    <style>
        body { 
            font-family: 'Courier New', monospace; 
            background: #000; 
            color: #00ff00; 
            padding: 20px;
            margin: 0;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
        }
        h1 { color: #ffffff; }
        .command { color: #ffff00; font-weight: bold; }
        .info { color: #00ffff; }
    </style>
</head>
<body>
    <div class="container">
        <h1>λ Lambda - Digital Entities Game</h1>
        <p>This is a telnet-based multiplayer game server.</p>
        
        <div class="info">
            <h3>Connection Instructions:</h3>
            <p><span class="command">telnet localhost 8181</span></p>
            
            <h3>Game Description:</h3>
            <p>Lambda is a hybrid board/computer game where players are electrical entities (Lambda race) 
            navigating a physical board with LED position tracking, collecting logic fragments, 
            and working to escape the system and invade the internet.</p>
            
            <h3>Features:</h3>
            <ul>
                <li>Multiplayer telnet-based gameplay</li>
                <li>Real-time movement and interaction</li>
                <li>Logic fragment collection system</li>
                <li>Defrag bot encounters with mini-games</li>
                <li>Special items and trading economy</li>
                <li>Coordinate repair system</li>
            </ul>
        </div>
        
        <p><strong>Note:</strong> Web interface disabled - game runs via telnet only.</p>
    </div>
</body>
</html>