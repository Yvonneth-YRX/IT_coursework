let startOverlay = null;
let startOverlayPulse = 0;
let startScreenShown = false;
let resultOverlay = null;
let resultOverlayPulse = 0;
let gameResult = null;
let gameResultArmed = false;
let autoStartNextGame = false;
let unitAnimationResetTimers = new Map();
let audioToggleButton = null;
let audioToggleLabel = null;
let gameCursorElement = null;

const battleMusic = {
	context: null,
	masterGain: null,
	compressor: null,
	started: false,
	muted: false,
	scheduleTimer: null,
	nextNoteTime: 0,
	stepIndex: 0,
	mood: "steady"
};

const battleMusicChords = [
	{ root: 146.83, fifth: 220.0, color: 293.66, bass: 73.42 },
	{ root: 174.61, fifth: 261.63, color: 349.23, bass: 87.31 },
	{ root: 130.81, fifth: 196.0, color: 261.63, bass: 65.41 },
	{ root: 196.0, fifth: 293.66, color: 392.0, bass: 98.0 }
];

const battleMusicProfiles = {
	steady: {
		tempo: 88,
		baseVolume: 0.15,
		padRoot: 0.026,
		padFifth: 0.017,
		padColor: 0.009,
		bass: 0.028,
		lead: 0.010,
		drumAccent: 0.1,
		drumLight: 0.055,
		extraDrumSteps: [],
		leadPattern: [1, 1.122, 1.335, 1.498],
		leadWave: "square",
		leadRelease: 0.08,
		bassPattern: [1, 1, 1.12, 1, 1.5, 1.12, 1, 0.92],
		tensionMultiplier: 1
	},
	advantage: {
		tempo: 100,
		baseVolume: 0.18,
		padRoot: 0.03,
		padFifth: 0.02,
		padColor: 0.011,
		bass: 0.04,
		lead: 0.017,
		drumAccent: 0.13,
		drumLight: 0.075,
		extraDrumSteps: [2, 6],
		leadPattern: [1, 1.122, 1.335, 1.682],
		leadWave: "square",
		leadRelease: 0.1,
		bassPattern: [1, 1.12, 1.5, 1.12, 1.68, 1.5, 1.12, 1],
		tensionMultiplier: 0.985
	},
	danger: {
		tempo: 94,
		baseVolume: 0.165,
		padRoot: 0.024,
		padFifth: 0.016,
		padColor: 0.012,
		bass: 0.033,
		lead: 0.013,
		drumAccent: 0.115,
		drumLight: 0.065,
		extraDrumSteps: [6],
		leadPattern: [1, 1.067, 1.335, 1.26],
		leadWave: "triangle",
		leadRelease: 0.07,
		bassPattern: [1, 0.94, 1.12, 0.94, 1.26, 1.12, 0.94, 0.9],
		tensionMultiplier: 1.03
	}
};

function initHexi(preloadImages) {
	
	//1. Setting up and starting Hexi

	//Initialize and start Hexi
	g = hexi(stageWidth, stageHeight, setup, preloadImages, load);
	g.fps = 60;
	g.border = "2px red dashed";
	g.backgroundColor = 0x000000;
	if (g.canvas) {
		g.canvas.classList.add("game-canvas");
		g.canvas.style.cursor = "none";
		attachGameCursor(g.canvas);
	}
	g.scaleToWindow();
	g.start();
	
}

function attachGameCursor(canvas) {
	if (!canvas) {
		return;
	}

	if (gameCursorElement === null) {
		gameCursorElement = document.createElement("div");
		gameCursorElement.className = "game-ui-cursor";
		document.body.appendChild(gameCursorElement);
	}

	function moveCursor(event) {
		if (!gameCursorElement) {
			return;
		}

		gameCursorElement.style.left = event.clientX + "px";
		gameCursorElement.style.top = event.clientY + "px";
	}

	canvas.addEventListener("mouseenter", function(event) {
		document.body.classList.add("game-cursor-active");
		canvas.style.cursor = "none";
		gameCursorElement.classList.add("visible");
		moveCursor(event);
	});

	canvas.addEventListener("mousemove", moveCursor);

	canvas.addEventListener("mouseleave", function() {
		document.body.classList.remove("game-cursor-active");
		canvas.style.cursor = "";
		gameCursorElement.classList.remove("visible");
		gameCursorElement.classList.remove("active");
		gameCursorElement.style.left = "-9999px";
		gameCursorElement.style.top = "-9999px";
	});

	canvas.addEventListener("mousedown", function() {
		gameCursorElement.classList.add("active");
	});

	canvas.addEventListener("mouseup", function() {
		gameCursorElement.classList.remove("active");
	});
}

function ensureBattleMusicContext() {
	if (battleMusic.context !== null) {
		return battleMusic.context;
	}

	var AudioContextClass = window.AudioContext || window.webkitAudioContext;
	if (!AudioContextClass) {
		return null;
	}

	var context = new AudioContextClass();
	var compressor = context.createDynamicsCompressor();
	var masterGain = context.createGain();

	compressor.threshold.value = -24;
	compressor.knee.value = 24;
	compressor.ratio.value = 10;
	compressor.attack.value = 0.003;
	compressor.release.value = 0.25;

	masterGain.gain.value = battleMusic.muted ? 0 : battleMusicProfiles[battleMusic.mood].baseVolume;
	masterGain.connect(compressor);
	compressor.connect(context.destination);

	battleMusic.context = context;
	battleMusic.masterGain = masterGain;
	battleMusic.compressor = compressor;
	return context;
}

function getBattleMusicProfile() {
	return battleMusicProfiles[battleMusic.mood] || battleMusicProfiles.steady;
}

function scheduleSynthVoice(frequency, startTime, duration, type, peakVolume, attack, release, detune, filterMultiplier, resonance) {
	if (!battleMusic.context || !battleMusic.masterGain) {
		return;
	}

	var oscillator = battleMusic.context.createOscillator();
	var voiceGain = battleMusic.context.createGain();
	var filter = battleMusic.context.createBiquadFilter();

	oscillator.type = type;
	oscillator.frequency.setValueAtTime(frequency, startTime);
	oscillator.detune.setValueAtTime(detune || 0, startTime);

	filter.type = "lowpass";
	filter.frequency.setValueAtTime(Math.max(220, frequency * (filterMultiplier || 6)), startTime);
	filter.Q.value = resonance || 0.8;

	voiceGain.gain.setValueAtTime(0.0001, startTime);
	voiceGain.gain.linearRampToValueAtTime(peakVolume, startTime + attack);
	voiceGain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration + release);

	oscillator.connect(filter);
	filter.connect(voiceGain);
	voiceGain.connect(battleMusic.masterGain);

	oscillator.start(startTime);
	oscillator.stop(startTime + duration + release + 0.05);
}

function scheduleBattleDrum(startTime, isAccent, peakVolume, pitch) {
	if (!battleMusic.context || !battleMusic.masterGain) {
		return;
	}

	var osc = battleMusic.context.createOscillator();
	var gain = battleMusic.context.createGain();
	var filter = battleMusic.context.createBiquadFilter();

	osc.type = "triangle";
	osc.frequency.setValueAtTime(pitch || (isAccent ? 96 : 82), startTime);
	osc.frequency.exponentialRampToValueAtTime(42, startTime + 0.16);

	filter.type = "lowpass";
	filter.frequency.setValueAtTime(180, startTime);

	gain.gain.setValueAtTime(0.0001, startTime);
	gain.gain.linearRampToValueAtTime(peakVolume, startTime + 0.01);
	gain.gain.exponentialRampToValueAtTime(0.0001, startTime + 0.2);

	osc.connect(filter);
	filter.connect(gain);
	gain.connect(battleMusic.masterGain);

	osc.start(startTime);
	osc.stop(startTime + 0.22);
}

function scheduleBattleStep(stepIndex, startTime, stepDuration) {
	var profile = getBattleMusicProfile();
	var chord = battleMusicChords[Math.floor(stepIndex / 8) % battleMusicChords.length];
	var stepInBar = stepIndex % 8;

	if (stepInBar === 0) {
		scheduleSynthVoice(chord.root, startTime, stepDuration * 7.6, "triangle", profile.padRoot, 0.8, 1.2, -4, 5.4, 0.9);
		scheduleSynthVoice(chord.fifth, startTime + 0.03, stepDuration * 7.3, "sine", profile.padFifth, 1.0, 1.3, 3, 6.4, 0.7);
		scheduleSynthVoice(chord.color * profile.tensionMultiplier, startTime + 0.06, stepDuration * 6.8, "triangle", profile.padColor, 1.1, 1.4, 0, 5.2, 1.1);
	}

	if (stepInBar === 0 || stepInBar === 4) {
		scheduleBattleDrum(startTime, stepInBar === 0, stepInBar === 0 ? profile.drumAccent : profile.drumLight, stepInBar === 0 ? 96 : 80);
	}

	if (profile.extraDrumSteps.indexOf(stepInBar) !== -1) {
		scheduleBattleDrum(startTime, false, profile.drumLight * 0.92, stepInBar === 6 ? 72 : 84);
	}

	if (stepInBar % 2 === 0) {
		scheduleSynthVoice(chord.bass * profile.bassPattern[stepInBar], startTime, stepDuration * 0.72, "sawtooth", profile.bass, 0.02, 0.16, -6, 3.4, 1.6);
	}

	if (stepInBar === 1 || stepInBar === 3 || stepInBar === 5 || stepInBar === 7) {
		var patternIndex = ((stepInBar - 1) / 2) % profile.leadPattern.length;
		scheduleSynthVoice(chord.root * profile.leadPattern[patternIndex], startTime, stepDuration * 0.42, profile.leadWave, profile.lead, 0.01, profile.leadRelease, 4, 8, 2.6);
		if (battleMusic.mood === "advantage" && stepInBar === 5) {
			scheduleSynthVoice(chord.fifth * 2, startTime + 0.02, stepDuration * 0.3, "triangle", profile.lead * 0.8, 0.01, 0.06, 0, 9, 2.1);
		}
		if (battleMusic.mood === "danger" && stepInBar === 7) {
			scheduleSynthVoice(chord.color * 0.943, startTime + 0.01, stepDuration * 0.26, "triangle", profile.lead * 0.55, 0.01, 0.05, -2, 6.8, 2.9);
		}
	}
}

function battleMusicScheduler() {
	if (!battleMusic.context) {
		return;
	}

	var stepDuration = 60 / getBattleMusicProfile().tempo / 2;
	while (battleMusic.nextNoteTime < battleMusic.context.currentTime + 0.45) {
		scheduleBattleStep(battleMusic.stepIndex, battleMusic.nextNoteTime, stepDuration);
		battleMusic.nextNoteTime += stepDuration;
		battleMusic.stepIndex += 1;
	}
}

function getBattleMusicMoodLabel() {
	if (battleMusic.mood === "advantage") {
		return "Music: On +";
	}
	if (battleMusic.mood === "danger") {
		return "Music: On !";
	}
	return "Music: On =";
}

function evaluateBattleMusicMood() {
	if (player1Health === null || player2Health === null) {
		return "steady";
	}

	var player1Value = parseInt(player1Health.text, 10);
	var player2Value = parseInt(player2Health.text, 10);
	if (isNaN(player1Value) || isNaN(player2Value)) {
		return "steady";
	}

	var healthDiff = player1Value - player2Value;
	if (healthDiff >= 6 || (healthDiff >= 3 && player1Value >= 15)) {
		return "advantage";
	}
	if (healthDiff <= -6 || (healthDiff <= -3 && player1Value <= 12)) {
		return "danger";
	}
	return "steady";
}

function syncBattleMusicMood() {
	var nextMood = evaluateBattleMusicMood();
	if (battleMusic.mood === nextMood) {
		return;
	}

	battleMusic.mood = nextMood;
	if (battleMusic.masterGain && battleMusic.context && !battleMusic.muted) {
		var profile = getBattleMusicProfile();
		battleMusic.masterGain.gain.cancelScheduledValues(battleMusic.context.currentTime);
		battleMusic.masterGain.gain.setTargetAtTime(profile.baseVolume, battleMusic.context.currentTime, 0.18);
	}
	updateAudioToggleButton();
}

function updateAudioToggleButton() {
	if (!audioToggleButton || !audioToggleLabel) {
		return;
	}

	audioToggleLabel.text = battleMusic.muted ? "Music: Off" : getBattleMusicMoodLabel();
	audioToggleButton.background.clear();
	audioToggleButton.background.beginFill(battleMusic.muted ? 0x4b5563 : 0x18324a, 0.92);
	audioToggleButton.background.lineStyle(3, battleMusic.muted ? 0xaab4c2 : 0xd8be79, 1);
	audioToggleButton.background.drawRoundedRect(0, 0, 210, 56, 14);
	audioToggleButton.background.endFill();
}

function createAudioToggleButton() {
	if (!g || !g.stage) {
		return;
	}

	if (audioToggleButton !== null) {
		g.stage.removeChild(audioToggleButton);
	}

	var button = new PIXI.Container();
	button.interactive = true;
	button.buttonMode = true;
	button.position.x = stageWidth - 250;
	button.position.y = 28;
	button.on("click", toggleBattleMusicMute);

	var background = new PIXI.Graphics();
	button.background = background;
	button.addChild(background);

	var label = new PIXI.Text("", { font: "24px Roboto", fill: "#f8fafc", fontWeight: "bold" });
	label.anchor.set(0.5, 0.5);
	label.position.x = 105;
	label.position.y = 28;
	button.addChild(label);

	audioToggleButton = button;
	audioToggleLabel = label;
	updateAudioToggleButton();
	g.stage.addChild(button);
}

function setBattleMusicMuted(muted) {
	battleMusic.muted = muted;
	if (battleMusic.masterGain && battleMusic.context) {
		var targetVolume = muted ? 0.0001 : getBattleMusicProfile().baseVolume;
		battleMusic.masterGain.gain.cancelScheduledValues(battleMusic.context.currentTime);
		battleMusic.masterGain.gain.setTargetAtTime(targetVolume, battleMusic.context.currentTime, 0.08);
	}
	updateAudioToggleButton();
}

function toggleBattleMusicMute() {
	setBattleMusicMuted(!battleMusic.muted);
}

function startBattleMusic() {
	var context = ensureBattleMusicContext();
	if (!context) {
		return;
	}

	if (context.state === "suspended") {
		context.resume();
	}

	if (battleMusic.started) {
		setBattleMusicMuted(battleMusic.muted);
		return;
	}

	battleMusic.started = true;
	battleMusic.mood = "steady";
	battleMusic.stepIndex = 0;
	battleMusic.nextNoteTime = context.currentTime + 0.05;
	battleMusic.scheduleTimer = window.setInterval(battleMusicScheduler, 120);
	battleMusicScheduler();
	syncBattleMusicMood();
	setBattleMusicMuted(battleMusic.muted);
}

function stopBattleMusic() {
	if (battleMusic.scheduleTimer !== null) {
		window.clearInterval(battleMusic.scheduleTimer);
		battleMusic.scheduleTimer = null;
	}

	battleMusic.started = false;
	battleMusic.mood = "steady";
}

//2. The `load` function that will run while your files are loading

function load(){
  
  //Display the file currently being loaded
  console.log(`loading: ${g.loadingFile}`); 

  //Display the percentage of files currently loaded
  console.log(`progress: ${g.loadingProgress}`);

  //Add an optional loading bar 
  g.loadingBar();
}

//3. The `setup` function, which initializes your game objects, variables and sprites

function setup() {
  renderBackground();

  // Set up Methods for triggering actions based on pointer clicks
  //g.pointer.press = () => {console.log("The pointer was pressed "+g.pointer.x+" "+g.pointer.y);}

  //Add a custom `release` method
  //g.pointer.release = () => {console.log("The pointer was released");}

  //Add a custom `tap` method
  //g.pointer.tap = () => {console.log("The pointer was tapped");}

  //Set the game state to `play` to start the game loop
  g.state = play;
}

function renderBackground() {
	var bg = g.sprite("assets/game/extra/battlemap6_middleground.png");
	bg.interactive = true;
	bg.on('click', bgClicked);
	bg.setPosition(0, 0);
	bg.width = stageWidth;
	bg.height = stageHeight;
	g.stage.addChild(bg);
	createAudioToggleButton();
}

function createOverlayButton(label, x, y, width, height, clickHandler) {
	var buttonContainer = new PIXI.Container();
	buttonContainer.interactive = true;
	buttonContainer.buttonMode = true;
	buttonContainer.on('click', clickHandler);

	var buttonSprite = g.sprite("assets/game/extra/ui/button_primary.png");
	buttonSprite.setPosition(0, 0);
	buttonSprite.width = width;
	buttonSprite.height = height;
	buttonContainer.addChild(buttonSprite);

	var buttonText = new PIXI.Text(label, { font: '32px Roboto', fill: 'white', align: 'center' });
	buttonText.anchor.set(0.5, 0.5);
	buttonText.position.x = width / 2;
	buttonText.position.y = height / 2;
	buttonContainer.addChild(buttonText);

	buttonContainer.position.x = x;
	buttonContainer.position.y = y;
	return buttonContainer;
}

function consumeAutoStartFlag() {
	if (autoStartNextGame) {
		return;
	}

	try {
		if (window.sessionStorage.getItem("autoStartNextGame") === "true") {
			autoStartNextGame = true;
		}
	} catch (error) {
		console.log(error);
	}
}

function showStartOverlay() {
	if (startOverlay !== null) {
		return;
	}

	var overlay = new PIXI.Container();

	var mask = new PIXI.Graphics();
	mask.beginFill(0x05070d, 0.88);
	mask.drawRect(0, 0, stageWidth, stageHeight);
	mask.endFill();
	mask.interactive = true;
	overlay.addChild(mask);

	var panel = new PIXI.Graphics();
	panel.beginFill(0x142238, 0.96);
	panel.lineStyle(6, 0xc7a75d, 1);
	panel.drawRoundedRect(0, 0, 760, 360, 24);
	panel.endFill();
	panel.position.x = 580;
	panel.position.y = 250;
	overlay.addChild(panel);

	var title = new PIXI.Text('START GAME', { font: '72px Roboto', fill: '#f5e6b4', fontWeight: 'bold' });
	title.anchor.set(0.5, 0.5);
	title.position.x = stageWidth / 2;
	title.position.y = 380;
	overlay.addChild(title);

	var subtitleText = autoStartNextGame ? 'Press the button to restart the match with music enabled.' : 'Press the button to begin the match.';
	var buttonLabel = autoStartNextGame ? 'Restart Match' : 'Start Game';

	var subtitle = new PIXI.Text(subtitleText, { font: '28px Roboto', fill: '#d9e3f0', align: 'center' });
	subtitle.anchor.set(0.5, 0.5);
	subtitle.position.x = stageWidth / 2;
	subtitle.position.y = 485;
	overlay.addChild(subtitle);

	var startButton = createOverlayButton(buttonLabel, 760, 560, 400, 96, startGameClicked);
	overlay.addChild(startButton);

	overlay.banner = title;
	overlay.panel = panel;
	startOverlay = overlay;
	startOverlayPulse = 0;
	g.stage.addChild(overlay);
}

function hideStartOverlay() {
	if (startOverlay === null) {
		return;
	}

	g.stage.removeChild(startOverlay);
	startOverlay = null;
}

function startGameClicked() {
	hideStartOverlay();
	startBattleMusic();
	autoStartNextGame = false;

	try {
		window.sessionStorage.removeItem("autoStartNextGame");
	} catch (error) {
		console.log(error);
	}

	if (gameStart) {
		return;
	}

	// Start each match from a clean client-side UI state so stale hand
	// containers from an earlier session cannot remain on screen.
	resetClientGameState();

	ws.send(JSON.stringify({
		messagetype: "initalize"
	}));

	gameStart = true;
	gameResult = null;
	gameResultArmed = false;
	renderPlayer1Card();
	renderPlayer2Card();
	renderEndTurnButton();
}

function showResultOverlay(result) {
	if (resultOverlay !== null || gameResult !== null) {
		return;
	}

	gameResult = result;

	var overlay = new PIXI.Container();

	var mask = new PIXI.Graphics();
	mask.beginFill(0x020202, 0.82);
	mask.drawRect(0, 0, stageWidth, stageHeight);
	mask.endFill();
	mask.interactive = true;
	overlay.addChild(mask);

	var panel = new PIXI.Graphics();
	var accent = result === "win" ? 0x58c46b : 0xd35d6e;
	panel.beginFill(0x111827, 0.96);
	panel.lineStyle(6, accent, 1);
	panel.drawRoundedRect(0, 0, 820, 400, 28);
	panel.endFill();
	panel.position.x = 550;
	panel.position.y = 220;
	overlay.addChild(panel);

	var titleText = result === "win" ? 'VICTORY' : 'DEFEAT';
	var subtitleText = result === "win" ? 'Player 2 has been defeated.' : 'Player 1 has been defeated.';
	var titleColor = result === "win" ? '#c8ffd1' : '#ffd0d6';

	var title = new PIXI.Text(titleText, { font: '96px Roboto', fill: titleColor, fontWeight: 'bold' });
	title.anchor.set(0.5, 0.5);
	title.position.x = stageWidth / 2;
	title.position.y = 365;
	overlay.addChild(title);

	var subtitle = new PIXI.Text(subtitleText, { font: '30px Roboto', fill: '#e5e7eb' });
	subtitle.anchor.set(0.5, 0.5);
	subtitle.position.x = stageWidth / 2;
	subtitle.position.y = 465;
	overlay.addChild(subtitle);

	var restartButton = createOverlayButton('Restart Game', 760, 540, 400, 96, restartGameClicked);
	overlay.addChild(restartButton);

	overlay.banner = title;
	overlay.panel = panel;
	resultOverlay = overlay;
	resultOverlayPulse = 0;
	g.stage.addChild(overlay);
	ensureOverlayZOrder();
}

function restartGameClicked() {
	try {
		window.sessionStorage.setItem("autoStartNextGame", "true");
	} catch (error) {
		console.log(error);
	}

	// Reload into a fresh websocket/game actor session so stale messages
	// from the previous game cannot overwrite the new hand UI.
	window.location.reload();
}

function resetClientGameState() {
	if (g && g.stage) {
		g.stage.removeChildren();
		renderBackground();
	}

	boardTiles = new Map();
	spriteContainers = new Map();
	sprites = new Map();
	attackLabels = new Map();
	healthLabels = new Map();
	handContainers = [null, null, null, null, null, null];
	handSprites = [null, null, null, null, null, null];
	cardJSON = [null, null, null, null, null, null];
	cardPreview = null;
	prevewCountdown = 0;
	activeMoves = new Map();
	activeProjectiles = [];
	drawUnitQueue = [];
	drawTileQueue = [];
	player1ManaIcons = new Map();
	player2ManaIcons = new Map();
	player1Health = null;
	player2Health = null;
	player1Notification = null;
	player2Notification = null;
	player1NotificationText = null;
	player2NotificationText = null;
	playingEffects = [];
	startOverlay = null;
	resultOverlay = null;
	startOverlayPulse = 0;
	resultOverlayPulse = 0;
	battleMusic.mood = "steady";
	updateAudioToggleButton();
}

function ensureOverlayZOrder() {
	if (!g || !g.stage) {
		return;
	}

	if (audioToggleButton !== null) {
		g.stage.removeChild(audioToggleButton);
		g.stage.addChild(audioToggleButton);
	}

	if (startOverlay !== null) {
		g.stage.removeChild(startOverlay);
		g.stage.addChild(startOverlay);
	}

	if (resultOverlay !== null) {
		g.stage.removeChild(resultOverlay);
		g.stage.addChild(resultOverlay);
	}
}

function updateOverlayAnimations() {
	if (startOverlay !== null) {
		startOverlayPulse = startOverlayPulse + 0.05;
		startOverlay.banner.scale.x = 1 + (Math.sin(startOverlayPulse) * 0.03);
		startOverlay.banner.scale.y = 1 + (Math.sin(startOverlayPulse) * 0.03);
		startOverlay.panel.alpha = 0.9 + (Math.sin(startOverlayPulse * 0.8) * 0.05);
	}

	if (resultOverlay !== null) {
		resultOverlayPulse = resultOverlayPulse + 0.06;
		resultOverlay.banner.scale.x = 1 + (Math.sin(resultOverlayPulse) * 0.025);
		resultOverlay.banner.scale.y = 1 + (Math.sin(resultOverlayPulse) * 0.025);
		resultOverlay.panel.alpha = 0.9 + (Math.sin(resultOverlayPulse * 0.7) * 0.06);
	}
}

function checkGameResult() {
	if (gameResult !== null || player1Health === null || player2Health === null) {
		return;
	}

	var player1Value = parseInt(player1Health.text, 10);
	var player2Value = parseInt(player2Health.text, 10);

	if (!gameResultArmed) {
		if (!isNaN(player1Value) && !isNaN(player2Value) && player1Value > 0 && player2Value > 0) {
			gameResultArmed = true;
		}
		return;
	}

	if (!isNaN(player1Value) && player1Value <= 0) {
		showResultOverlay("lose");
	} else if (!isNaN(player2Value) && player2Value <= 0) {
		showResultOverlay("win");
	}
}

function bgClicked() {
	ws.send(JSON.stringify({
    		messagetype: "otherclicked"
  	}));
}

function drawTile(message) {
	
	var tileid = message.tile.tilex+"-"+message.tile.tiley;
	
	if (boardTiles.has(tileid)) {
		var tile = boardTiles.get(tileid);
		tile.show(message.mode);
		
	} else {
		tile = g.sprite(message.tile.tileTextures);
    	tile.setPosition(message.tile.xpos, message.tile.ypos);
    	tile.width = message.tile.width;
    	tile.height = message.tile.height;
		tile.tilex = message.tile.tilex;
		tile.tiley = message.tile.tiley;
		tile.show(message.mode);
		tile.interactive = true;
		tile.on('click', tileClicked);
    	g.stage.addChild(tile);

		boardTiles.set(tileid, tile);
	}
	
	//console.log(message);
	
}

function tileClicked(eventData) {
	if (activeMoves.size > 0) {
		return;
	}

	ws.send(JSON.stringify({
    		messagetype: "tileclicked",
            tilex: eventData.target.tilex,
            tiley: eventData.target.tiley,
  	}));
}

function drawCard(message) {
	
	var handIndex = message.position-1; // correct for indices starting from 0
	
	if (handContainers[handIndex]!=null) {
		// delete the container before drawing the new one
		g.stage.removeChild(handContainers[handIndex]);
		handContainers[handIndex] = null;
	}
	
	var cardContainer = new PIXI.Container();
	
	cardJSON[handIndex] = message.card;
	
	var backgroundCardImage = g.sprite(message.card.miniCard.cardTextures);
	backgroundCardImage.show(message.mode);
	backgroundCardImage.setPosition(0, 0);
    backgroundCardImage.width = 200;
    backgroundCardImage.height = 200;
	backgroundCardImage.cardindex = handIndex+1;
	backgroundCardImage.interactive = true;
	backgroundCardImage.on('click', cardClicked);
	cardContainer.addChild(backgroundCardImage);
	
	var cardSprite = g.sprite(message.card.miniCard.animationFrames);

	if (message.card.isCreature) {
		cardSprite.fps = message.card.miniCard.fps;
		cardSprite.loop = true;
		cardSprite.playAnimation();
	} else if (message.mode === 0) {
		cardSprite.show(message.card.miniCard.index);
	} else {
		cardSprite.playAnimation();
	}
	
	cardSprite.setPosition(50, 50);
    cardSprite.width = 100;
    cardSprite.height = 100;
	cardContainer.addChild(cardSprite);
	handSprites[handIndex] = cardSprite;
	
	var cardNameBackground = g.sprite("assets/game/extra/ui/button_end_turn_enemy.png");
	cardNameBackground.setPosition(0, 140);
    cardNameBackground.width = 220;
    cardNameBackground.height = 50;
	cardContainer.addChild(cardNameBackground);
	
	var cardText = new PIXI.Text(message.card.cardname, { font: '18px Roboto', fill: 'white', align: 'center' });
	cardText.position.x = 30;
	cardText.position.y = 155;
	cardContainer.addChild(cardText);
	
	var manacircle = g.sprite("assets/game/extra/ManaCircle.png");
    manacircle.setPosition(120, 15);
    manacircle.width = 50;
    manacircle.height = 50;
	cardContainer.addChild(manacircle);
	
	var manaText = new PIXI.Text(message.card.manacost, { font: '25px Roboto', fill: 'white', align: 'center' });
	manaText.position.x = 138;
	manaText.position.y = 25;
	cardContainer.addChild(manaText);
	handContainers[handIndex] = cardContainer;

	g.stage.addChild(cardContainer);
	layoutHandCards();
	ensureOverlayZOrder();
}

function layoutHandCards() {
	var spacing = 165;
	var startX = 290;
	var y = 840;
	var visibleIndex = 0;

	for (var i = 0; i < handContainers.length; i++) {
		var container = handContainers[i];
		if (container == null) {
			continue;
		}

		container.position.x = startX + (visibleIndex * spacing);
		container.position.y = y;
		visibleIndex = visibleIndex + 1;
	}
}

function renderCardPreview(position) {
	
	if (cardPreview!=null) {
		g.stage.removeChild(cardPreview);
		cardPreview = null;
	}
	
	var card = cardJSON[position-1];
	
	var previewContainer = new PIXI.Container();
	
	
	var backgroundCardImage = g.sprite(card.bigCard.cardTextures);
	backgroundCardImage.show(0);
	backgroundCardImage.setPosition(0, 0);
    backgroundCardImage.width = 250;
    backgroundCardImage.height = 350;
	previewContainer.addChild(backgroundCardImage);
	
	var cardText = new PIXI.Text(card.cardname, { font: '20px Roboto', fill: 'white', align: 'center' });
	cardText.position.x = 30;
	cardText.position.y = 30;
	previewContainer.addChild(cardText);
	
	var cardSprite = g.sprite(card.miniCard.animationFrames);
	cardSprite.playAnimation();
	cardSprite.setPosition(75, 65);
    cardSprite.width = 100;
    cardSprite.height = 100;
	previewContainer.addChild(cardSprite);
	
	var manacircle = g.sprite("assets/game/extra/ManaCircle.png");
    manacircle.setPosition(190, 15);
    manacircle.width = 50;
    manacircle.height = 50;
	previewContainer.addChild(manacircle);
	
	var manaText = new PIXI.Text(card.manacost, { font: '25px Roboto', fill: 'white', align: 'center' });
	manaText.position.x = 210;
	manaText.position.y = 25;
	previewContainer.addChild(manaText);
	
	for (i = 0; i < card.bigCard.rulesTextRows.length; i++) {
		var line = card.bigCard.rulesTextRows[i];
		var rulesLine = new PIXI.Text(line, { font: '15px Roboto', fill: 'white', align: 'center' });
		rulesLine.position.x = 30;
		rulesLine.position.y = 250+(i*20);
		previewContainer.addChild(rulesLine);
	}
	
	if (card.bigCard.attack>-1) {
		var cardAttack = new PIXI.Text(card.bigCard.attack, { font: '20px Roboto', fill: 'white', align: 'center' });
		cardAttack.position.x = 60;
		cardAttack.position.y = 200;
		previewContainer.addChild(cardAttack);
		
		var cardHealth = new PIXI.Text(card.bigCard.health, { font: '20px Roboto', fill: 'white', align: 'center' });
		cardHealth.position.x = 180;
		cardHealth.position.y = 200;
		previewContainer.addChild(cardHealth);
	}
	
	previewContainer.position.x = 1600;
	previewContainer.position.y = 400;
	
	
	
	prevewCountdown = 300;
	g.stage.addChild(previewContainer);
	cardPreview = previewContainer;
}

function cardClicked(eventData) {
	renderCardPreview(eventData.target.cardindex);
	ws.send(JSON.stringify({
    		messagetype: "cardclicked",
            position: eventData.target.cardindex
  	}));
}


function drawUnit(message) {
	
	//console.log(message.unit);

	var existingContainer = spriteContainers.get(message.unit.id);
	if (existingContainer) {
		g.stage.removeChild(existingContainer);
		spriteContainers.delete(message.unit.id);
		sprites.delete(message.unit.id);
		healthLabels.delete(message.unit.id);
		attackLabels.delete(message.unit.id);
	}

	var unitContainer = new PIXI.Container();
	
	// Draw unit in idle stance
	var unit = g.sprite(message.unit.animations.allFrames);
	unit.playAnimation(message.unit.animations.idle.frameStartEndIndices);
	unit.loop = message.unit.animations.idle.loop;
	unit.fps = message.unit.animations.idle.fps;
	
	var spriteX = message.unit.position.xpos - message.unit.correction.spriteTopLeftX;
	var spriteY = message.unit.position.ypos - message.unit.correction.spriteTopLeftY-20;
	
	var renderedWidth = message.unit.correction.imgWidth*(1+(message.unit.correction.spriteTopLeftX/message.unit.correction.imgWidth))*message.unit.correction.scale;
	var renderedHeight = message.unit.correction.imgHeight*(1+(message.unit.correction.spriteTopLeftY/message.unit.correction.imgHeight))*message.unit.correction.scale;
    unit.width = renderedWidth;
    unit.height = renderedHeight;
	unit.gameID = message.unit.id;
	
	// if reflect, flip the unit sprite
	if (message.unit.correction.reflected) {
		unit.scale.x = -1 * Math.abs(unit.scale.x);
		unit.setPosition(renderedWidth + message.unit.correction.offsetX, message.unit.correction.offsetY);
	} else {
		unit.setPosition(message.unit.correction.offsetX, message.unit.correction.offsetY);
	}
	
	//unit.interactive = true;
	//unit.on('click', unitClicked);
    unitContainer.addChild(unit);

	// Draw attack value
	var attackcircle = g.sprite("assets/game/extra/AttackCircle.png");
    attackcircle.setPosition(message.unit.correction.spriteTopLeftX+5, message.unit.correction.spriteTopLeftY+message.tile.height-25);
    attackcircle.width = 40;
    attackcircle.height = 40;
	unitContainer.addChild(attackcircle);
	
	var attackText = new PIXI.Text('0', { font: '20px Roboto', fill: 'white', align: 'center' });
	attackText.position.x = message.unit.correction.spriteTopLeftX+20;
	attackText.position.y = message.unit.correction.spriteTopLeftY+message.tile.height-15;
	unitContainer.addChild(attackText);
	
	// Draw health value
	var healthcircle = g.sprite("assets/game/extra/HealthCircle.png");
    healthcircle.setPosition(message.unit.correction.spriteTopLeftX+message.tile.height-45, message.unit.correction.spriteTopLeftY+message.tile.height-25);
    healthcircle.width = 40;
    healthcircle.height = 40;
	unitContainer.addChild(healthcircle);
	
	var healthText = new PIXI.Text('0', { font: '20px Roboto', fill: 'white', align: 'center' });
	healthText.position.x = message.unit.correction.spriteTopLeftX+message.tile.height-30;
	healthText.position.y = message.unit.correction.spriteTopLeftY+message.tile.height-15;
	unitContainer.addChild(healthText);

	unitContainer.position.x = spriteX;
	unitContainer.position.y = spriteY;

	g.stage.addChild(unitContainer);
	ensureOverlayZOrder();

	spriteContainers.set(message.unit.id, unitContainer);
	sprites.set(message.unit.id, unit);
	healthLabels.set(message.unit.id, healthText);
	attackLabels.set(message.unit.id, attackText);
	
	
	

}

window.addEventListener("beforeunload", stopBattleMusic);


function getFrameSet(unit) {
	var frameSet = [];
	var anim = unit.animation;
	
	if (anim === "idle") {
		frameSet = unit.animations.idle;
	}
	if (anim === "death") {
		frameSet = unit.animations.death;
	}
	if (anim === "attack") {
		frameSet = unit.animations.attack;
	}
	if (anim === "move") {
		frameSet = unit.animations.move;
	}
	if (anim === "channel") {
		frameSet = unit.animations.channel;
	}
	if (anim === "hit") {
		frameSet = unit.animations.hit;
	}
	
	console.log(frameSet);
	
	return frameSet;
}

// Starts a move action for a Unit
function moveUnit(unitID, xTile, yTile) {
	ws.send(JSON.stringify({
    		messagetype: "getTileForMove",
			unitID: unitID,
			xTile: xTile,
			yTile: yTile
  		}));
}

function moveUnitToTile(message) {
	var moveKey = message.unit && message.unit.id !== undefined ? message.unit.id : message.unitID;
	if (moveKey === undefined) {
		return;
	}

	activeMoves.set(moveKey, message);
}

// Performs a single frame move towards the target destination for a sprite
// Returns whether the destination has been reached
function executeMoveStep(message) {
	if (!message || !message.unit || message.unit.id === undefined || !message.tile) {
		return true;
	}

	var targetUnit = sprites.get(message.unit.id);
	var targetContainer = spriteContainers.get(message.unit.id);

	if (!targetUnit || !targetContainer) {
		return true;
	}
	
	if (message.unit.animation !== "move") {
		targetUnit.stopAnimation();
		
		ws.send(JSON.stringify({
    		messagetype: "unitMoving",
			id: message.unit.id
  		}));
		
		message.unit.animation = "move";
		targetUnit.fps = message.unit.animations.move.fps;
		targetUnit.loop = message.unit.animations.move.loop;
		targetUnit.playAnimation(message.unit.animations.move.frameStartEndIndices);
		//sprite.interactive = false;
	}
	
	
	//console.log(moveMessage)
	
	var spriteX = message.tile.xpos - message.unit.correction.spriteTopLeftX;
	var spriteY = message.tile.ypos - message.unit.correction.spriteTopLeftY-20;
	
	var dx = Math.abs(targetContainer.position.x - spriteX);
    var dy = Math.abs(targetContainer.position.y - spriteY);

	//console.log("d:"+dx+" "+dy);

    if ((dx + dy) > 0) {
        
		if (message.yfirst === true) {
			if (dy>0) {
				if (dy>0) {
					if (targetContainer.position.y - spriteY < 0) {
						targetContainer.position.vy = Math.min(moveVelocity,dy);
						targetContainer.position.vx = 0;
					} else {
						targetContainer.position.vy = Math.max(-moveVelocity,-dy);
						targetContainer.position.vx = 0;
					}
				}
			} else {
				if (targetContainer.position.x - spriteX < 0) {
					targetContainer.position.vx = Math.min(moveVelocity, dx);
					targetContainer.position.vy = 0;
				} else {
					targetContainer.position.vx = Math.max(-moveVelocity, -dx);
					targetContainer.position.vy = 0;
				}
				
			}
		} else {
			if (dx>0) {
				if (targetContainer.position.x - spriteX < 0) {
					targetContainer.position.vx = Math.min(moveVelocity, dx);
					targetContainer.position.vy = 0;
				} else {
					targetContainer.position.vx = Math.max(-moveVelocity, -dx);
					targetContainer.position.vy = 0;
				}
			} else {
				if (dy>0) {
					if (targetContainer.position.y - spriteY < 0) {
						targetContainer.position.vy = Math.min(moveVelocity,dy);
						targetContainer.position.vx = 0;
					} else {
						targetContainer.position.vy = Math.max(-moveVelocity,-dy);
						targetContainer.position.vx = 0;
					}
				}
			}
		}

		//console.log(targetContainer.position.x+" "+targetContainer.position.y);

		

		g.move(targetContainer.position);
		
		dx = Math.abs(targetContainer.position.x - spriteX);
		dy = Math.abs(targetContainer.position.y - spriteY);
		
		if ((dx + dy) <= moveVelocity) {
			targetContainer.position.x = spriteX;
			targetContainer.position.y = spriteY;
		}

		return false
    } else {

	  var sprite = sprites.get(message.unit.id);
	  if (!sprite) {
	  	return true;
	  }

	  sprite.stopAnimation();
	  targetContainer.position.vx = 0;
	  targetContainer.position.vy = 0;

	  ws.send(JSON.stringify({
    		messagetype: "unitstopped",
			id: message.unit.id,
			tilex: message.tile.tilex,
			tiley: message.tile.tiley
  	  }));

	  message.unit.animation = "idle";
	  targetUnit.fps = message.unit.animations.idle.fps;
      targetUnit.loop = message.unit.animations.idle.loop;
	  targetUnit.playAnimation(message.unit.animations.idle.frameStartEndIndices);

      return true;
    }
}

function drawProjectile(message) {
	
	var projectile = g.sprite(message.effect.animationTextures);
	var effectX = message.tile.xpos - message.effect.correction.spriteTopLeftX;
	var effectY = message.tile.ypos - message.effect.correction.spriteTopLeftY;
	projectile.setPosition(effectX+message.effect.correction.offsetX, effectY+message.effect.correction.offsetY);
    projectile.width = message.effect.correction.imgWidth*(1+(message.effect.correction.spriteTopLeftX/message.effect.correction.imgWidth))*message.effect.correction.scale;
    projectile.height = message.effect.correction.imgHeight*(1+(message.effect.correction.spriteTopLeftY/message.effect.correction.imgHeight))*message.effect.correction.scale;
	projectile.show(message.mode);
	
	// if reflect, flip the unit sprite
	if (message.effect.correction.reflected) {
		projectile.scale.x = -1*message.effect.correction.scale;
		projectile.setPosition((message.effect.correction.imgWidth*message.effect.correction.scale)+message.effect.correction.offsetX, message.effect.correction.offsetY);
	}
	
	projectile.tile = message.tile;
	projectile.targetTile = message.targetTile;
	projectile.effect = message.effect;
	
	g.stage.addChild(projectile);
	ensureOverlayZOrder();
	
	activeProjectiles.push(projectile)

}

function executeProjectileMoveStep(projectile) {
	
	
	var tile = projectile.tile;
	var targetTile = projectile.targetTile;
	var effect = projectile.effect;
	
	var xvelocity = Math.abs(tile.tilex-targetTile.tilex)*2;
	var yvelocity = Math.abs(tile.tiley-targetTile.tiley)*2;
	
	var spriteX = targetTile.xpos - effect.correction.spriteTopLeftX+effect.correction.offsetX;
	var spriteY = targetTile.ypos - effect.correction.spriteTopLeftY+effect.correction.offsetY;
	
	var dx = Math.abs(projectile.position.x - spriteX);
    var dy = Math.abs(projectile.position.y - spriteY);

	//console.log("d:"+dx+" "+dy);

    if ((dx+dy) > 0) {
        
		//console.log(targetContainer.position.x+" "+targetContainer.position.y);

		if (dx>0) {
			if (projectile.position.x - spriteX < 0) {
				projectile.position.vx = Math.min(xvelocity, dx);
			} else {
				projectile.position.vx = Math.max(-xvelocity, -dx);
			}
		} else {
			projectile.position.vx = 0;
		}
		if (dy>0) {
			if (projectile.position.y - spriteY < 0) {
				projectile.position.vy = Math.min(yvelocity,dy);
			}else {
				projectile.position.vy = Math.max(-yvelocity,-dy);
			}
		} else {
			projectile.position.vy = 0;
		}

		g.move(projectile.position);
		
		return false
    } else {
		g.stage.removeChild(projectile);
		return true;
	}
}


function setUnitHealth(message) {
	var unitID = message.unit.id;
	var health = message.health;
	
	
	var oldHealth = parseInt(healthLabels.get(unitID).text);
	healthLabels.get(unitID).text = health;
	
	if (health>9 && oldHealth<10) {
		healthLabels.get(unitID).position.x = healthLabels.get(unitID).position.x-5;
	} else if (health<10 && oldHealth>9) {
		healthLabels.get(unitID).position.x = healthLabels.get(unitID).position.x+5;
	}
	
}

function setUnitAttack(message) {
	var unitID = message.unit.id;
	var attack = message.attack;
	
	
	var oldAttack = parseInt(attackLabels.get(unitID).text);
	attackLabels.get(unitID).text = attack;
	
	if (attack>9 && oldAttack<10) {
		attackLabels.get(unitID).position.x = attackLabels.get(unitID).position.x-5;
	} else if (attack<10 && oldAttack>9) {
		attackLabels.get(unitID).position.x = attackLabels.get(unitID).position.x+5;
	}
	
}


function renderPlayer1Card() {
	
	var icons = ["assets/game/extra/ui/icon_mana_inactive.png","assets/game/extra/ui/icon_mana.png"];
	
	var health = g.sprite("assets/game/extra/ui/notification_quest_small.png");
    health.setPosition(30, 250);
    health.width = 350;
    health.height = 110;
	g.stage.addChild(health);
	
	var healthTitle = new PIXI.Text('Life', { font: '28px Roboto', fill: 'white', align: 'center' });
	healthTitle.position.x = 120;
	healthTitle.position.y = 285;
	g.stage.addChild(healthTitle);
	
	player1Health = new PIXI.Text('0', { font: '28px Roboto', fill: 'white', align: 'center' });
	player1Health.position.x = 280;
	player1Health.position.y = 285;
	g.stage.addChild(player1Health);
	
	
	var mana1 = g.sprite(icons);
    mana1.setPosition(60, 400);
    mana1.width = 80;
    mana1.height = 80;
	mana1.show(0);
	g.stage.addChild(mana1);
	player1ManaIcons.set(1, mana1);
	
	var mana2 = g.sprite(icons);
    mana2.setPosition(60+50, 450);
    mana2.width = 80;
    mana2.height = 80;
	mana2.show(0);
	g.stage.addChild(mana2);
	player1ManaIcons.set(2, mana2);
	
	var mana3 = g.sprite(icons);
    mana3.setPosition(60, 500);
    mana3.width = 80;
    mana3.height = 80;
	mana3.show(0);
	g.stage.addChild(mana3);
	player1ManaIcons.set(3, mana3);
	
	var mana4 = g.sprite(icons);
    mana4.setPosition(60+50, 550);
    mana4.width = 80;
    mana4.height = 80;
	mana4.show(0);
	g.stage.addChild(mana4);
	player1ManaIcons.set(4, mana4);
	
	var mana5 = g.sprite(icons);
    mana5.setPosition(60, 600);
    mana5.width = 80;
    mana5.height = 80;
	mana5.show(0);
	g.stage.addChild(mana5);
	player1ManaIcons.set(5, mana5);
	
	var mana6 = g.sprite(icons);
    mana6.setPosition(60+50, 650);
    mana6.width = 80;
    mana6.height = 80;
	mana6.show(0);
	g.stage.addChild(mana6);
	player1ManaIcons.set(6, mana6);
	
	var mana7 = g.sprite(icons);
    mana7.setPosition(60, 700);
    mana7.width = 80;
    mana7.height = 80;
	mana7.show(0);
	g.stage.addChild(mana7);
	player1ManaIcons.set(7, mana7);
	
	var mana8 = g.sprite(icons);
    mana8.setPosition(60+50, 750);
    mana8.width = 80;
    mana8.height = 80;
	mana8.show(0);
	g.stage.addChild(mana8);
	player1ManaIcons.set(8, mana8);
	
	var mana9 = g.sprite(icons);
    mana9.setPosition(60, 800);
    mana9.width = 80;
    mana9.height = 80;
	mana9.show(0);
	g.stage.addChild(mana9);
	player1ManaIcons.set(9, mana9);
	
	var player1Portrait = g.sprite("assets/game/extra/ui/general_portrait_image_hex_f4-third@2x.png");
    player1Portrait.setPosition(65, 20);
    player1Portrait.width = 300;
    player1Portrait.height = 300;
	g.stage.addChild(player1Portrait);
	
	
	
}

function renderEndTurnButton() {
	var endTurnButton = g.sprite("assets/game/extra/ui/button_primary.png");
    endTurnButton.setPosition(1600, 950);
    endTurnButton.width = 300;
    endTurnButton.height = 100;
	endTurnButton.on('click', endturnClicked);
	endTurnButton.interactive = true;
	g.stage.addChild(endTurnButton);
	
	var endTurnText = new PIXI.Text('End Turn', { font: '28px Roboto', fill: 'white', align: 'center' });
	endTurnText.position.x = 1700;
	endTurnText.position.y = 980;
	g.stage.addChild(endTurnText);
	
}

function endturnClicked() {
	ws.send(JSON.stringify({
    		messagetype: "endturnclicked"
  	}));
}


function renderPlayer2Card() {

	var moveRight = 1500;
	var icons = ["assets/game/extra/ui/icon_mana_inactive.png","assets/game/extra/ui/icon_mana.png"];


	var health = g.sprite("assets/game/extra/ui/notification_quest_small.png");
    health.setPosition(moveRight+30, 250);
    health.width = 350;
    health.height = 110;
	g.stage.addChild(health);
	
	var healthTitle = new PIXI.Text('Life', { font: '28px Roboto', fill: 'white', align: 'center' });
	healthTitle.position.x = moveRight+120;
	healthTitle.position.y = 285;
	g.stage.addChild(healthTitle);
	
	player2Health = new PIXI.Text('0', { font: '28px Roboto', fill: 'white', align: 'center' });
	player2Health.position.x = moveRight+280;
	player2Health.position.y = 285;
	g.stage.addChild(player2Health);
	
	
	var mana1 = g.sprite(icons);
    mana1.setPosition(moveRight+200, 400);
    mana1.width = 80;
    mana1.height = 80;
	mana1.show(0);
	g.stage.addChild(mana1);
	player2ManaIcons.set(1, mana1);
	
	var mana2 = g.sprite(icons);
    mana2.setPosition(moveRight+200+50, 450);
    mana2.width = 80;
    mana2.height = 80;
	mana2.show(0);
	g.stage.addChild(mana2);
	player2ManaIcons.set(2, mana2);
	
	var mana3 = g.sprite(icons);
    mana3.setPosition(moveRight+200, 500);
    mana3.width = 80;
    mana3.height = 80;
	mana3.show(0);
	g.stage.addChild(mana3);
	player2ManaIcons.set(3, mana3);
	
	var mana4 = g.sprite(icons);
    mana4.setPosition(moveRight+200+50, 550);
    mana4.width = 80;
    mana4.height = 80;
	mana4.show(0);
	g.stage.addChild(mana4);
	player2ManaIcons.set(4, mana4);
	
	var mana5 = g.sprite(icons);
    mana5.setPosition(moveRight+200, 600);
    mana5.width = 80;
    mana5.height = 80;
	mana5.show(0);
	g.stage.addChild(mana5);
	player2ManaIcons.set(5, mana5);
	
	var mana6 = g.sprite(icons);
    mana6.setPosition(moveRight+200+50, 650);
    mana6.width = 80;
    mana6.height = 80;
	mana6.show(0);
	g.stage.addChild(mana6);
	player2ManaIcons.set(6, mana6);
	
	var mana7 = g.sprite(icons);
    mana7.setPosition(moveRight+200, 700);
    mana7.width = 80;
    mana7.height = 80;
	mana7.show(0);
	g.stage.addChild(mana7);
	player2ManaIcons.set(7, mana7);
	
	var mana8 = g.sprite(icons);
    mana8.setPosition(moveRight+200+50, 750);
    mana8.width = 80;
    mana8.height = 80;
	mana8.show(0);
	g.stage.addChild(mana8);
	player2ManaIcons.set(8, mana8);
	
	var mana9 = g.sprite(icons);
    mana9.setPosition(moveRight+200, 800);
    mana9.width = 80;
    mana9.height = 80;
	mana9.show(0);
	g.stage.addChild(mana9);
	player2ManaIcons.set(9, mana9);
	
	var player2Portrait = g.sprite("assets/game/extra/ui/general_portrait_image_hex_f1-third@2x.png");
    player2Portrait.setPosition(moveRight+65, 20);
    player2Portrait.width = 300;
    player2Portrait.height = 300;
	g.stage.addChild(player2Portrait);
	
}


function setPlayer1Health(message) {
	player1Health.text = message.player.health;
	syncBattleMusicMood();
	checkGameResult();
}

function setPlayer2Health(message) {
	player2Health.text = message.player.health;
	syncBattleMusicMood();
	checkGameResult();
}

function setPlayer1Mana(message) {
	
	var mana = message.player.mana;
	
	for (i = 1; i < 10; i++) {
		if (mana>=i) player1ManaIcons.get(i).show(1);
		else player1ManaIcons.get(i).show(0);
	}	
}

function setPlayer2Mana(message) {
	
	var mana = message.player.mana;
	
	for (i = 1; i < 10; i++) {
		if (mana>=i) player2ManaIcons.get(i).show(1);
		else player2ManaIcons.get(i).show(0);
	}	
}

function addPlayer1Notification(message) {
	if (player1Notification==null) {
		// we need to create a new notification
		
		player1Notification = g.sprite("assets/game/extra/ui/tooltip_left@2x.png");
    	player1Notification.setPosition(320, 100);
    	player1Notification.width = 800;
    	player1Notification.height = 150;
		player1Notification.countdown = message.seconds*60;
		g.stage.addChild(player1Notification);
		
		player1NotificationText = new PIXI.Text(message.text, { font: '35px Roboto', fill: 'white', align: 'center' });
		player1NotificationText.position.x = 460;
		player1NotificationText.position.y = 150;
		g.stage.addChild(player1NotificationText);
	} else {
		player1Notification.countdown = message.seconds*60;
		player1NotificationText.text = message.text;
	}
}

function addPlayer2Notification(message) {
	if (player2Notification==null) {
		// we need to create a new notification
		
		player2Notification = g.sprite("assets/game/extra/ui/tooltip_right@2x.png");
    	player2Notification.setPosition(320, 100);
    	player2Notification.width = 800;
    	player2Notification.height = 150;
		player2Notification.countdown = message.seconds*60;
		g.stage.addChild(player2Notification);
		
		player2NotificationText = new PIXI.Text(message.text, { font: '35px Roboto', fill: 'white', align: 'center' });
		player2NotificationText.position.x = 460;
		player2NotificationText.position.y = 150;
		g.stage.addChild(player2NotificationText);
	} else {
		player2Notification.countdown = message.seconds*60;
		player2NotificationText.text = message.text;
	}
}


function playUnitAnimation(message) {
	
	var targetUnit = sprites.get(message.unit.id);
	if (!targetUnit) {
		return;
	}

	var existingResetTimer = unitAnimationResetTimers.get(message.unit.id);
	if (existingResetTimer) {
		clearTimeout(existingResetTimer);
		unitAnimationResetTimers.delete(message.unit.id);
	}

	var animationData = getFrameSet(message.unit);
	targetUnit.loop = animationData.loop;
	targetUnit.fps = animationData.fps;
	targetUnit.playAnimation(animationData.frameStartEndIndices);

	if (message.unit.animation === "death" || animationData.loop) {
		return;
	}

	var frameCount = Math.max(1, (animationData.frameStartEndIndices[1] - animationData.frameStartEndIndices[0]) + 1);
	var animationDurationMs = Math.max(120, Math.round((frameCount / Math.max(1, animationData.fps)) * 1000));
	var resetTimer = setTimeout(function() {
		var sprite = sprites.get(message.unit.id);
		if (!sprite) {
			unitAnimationResetTimers.delete(message.unit.id);
			return;
		}

		sprite.loop = message.unit.animations.idle.loop;
		sprite.fps = message.unit.animations.idle.fps;
		sprite.playAnimation(message.unit.animations.idle.frameStartEndIndices);
		unitAnimationResetTimers.delete(message.unit.id);
	}, animationDurationMs);

	unitAnimationResetTimers.set(message.unit.id, resetTimer);
}

function deleteCard(message) {
	var card = handContainers[message.position-1];
	if (card != null) {
		g.stage.removeChild(card);
	}
	handContainers[message.position-1]=null;
	handSprites[message.position-1]=null;
	cardJSON[message.position-1]=null;
	prevewCountdown = 0;
	layoutHandCards();
}

function deleteUnit(message) {
	var unitContainer = spriteContainers.get(message.unit.id);
	g.stage.removeChild(unitContainer);
	var existingResetTimer = unitAnimationResetTimers.get(message.unit.id);
	if (existingResetTimer) {
		clearTimeout(existingResetTimer);
		unitAnimationResetTimers.delete(message.unit.id);
	}
	spriteContainers.delete(message.unit.id);
	sprites.delete(message.unit.id);
	attackLabels.delete(message.unit.id);
	healthLabels.delete(message.unit.id);
}


function playEffectAnimation(message) {
	
	var effect = g.sprite(message.effect.animationTextures);
	var effectX = message.tile.xpos - message.effect.correction.spriteTopLeftX;
	var effectY = message.tile.ypos - message.effect.correction.spriteTopLeftY;
	effect.setPosition(effectX+message.effect.correction.offsetX, effectY+message.effect.correction.offsetY);
    effect.width = message.effect.correction.imgWidth*(1+(message.effect.correction.spriteTopLeftX/message.effect.correction.imgWidth))*message.effect.correction.scale;
    effect.height = message.effect.correction.imgHeight*(1+(message.effect.correction.spriteTopLeftY/message.effect.correction.imgHeight))*message.effect.correction.scale;
	effect.fps = message.effect.fps;
	effect.loop = false;
	effect.playAnimation();
	g.stage.addChild(effect);
	ensureOverlayZOrder();
	
	var frameDiff = (60/effect.fps)+1;
	effect.killCountdown = frameDiff*message.effect.animationTextures.length;
	
	console.log(effect);
	
	playingEffects.push(effect);
}


//4. The `play` function, which is your game or application logic that runs in a loop

function play(){
  //This is your game loop, where you can move sprites and add your
  //game logic

	if (gameActorInitalized) {
	if (!startScreenShown) {
		startScreenShown = true;
		consumeAutoStartFlag();
		showStartOverlay();
	}

	if (!gameStart) {
		updateOverlayAnimations();
		return;
	}
	
	// Draw Tile Actions
	while (drawTileQueue.length>0) {
		drawTile(drawTileQueue.pop());
	}
	
	// Draw Tile Actions
	while (drawUnitQueue.length>0) {
		drawUnit(drawUnitQueue.pop());
	}
	
	var continuingProjectiles = [];
	for (i = 0; i < activeProjectiles.length; i++) {
		if(!executeProjectileMoveStep(activeProjectiles[i])) {
			continuingProjectiles.push(activeProjectiles[i]);
		}
	}
	activeProjectiles = continuingProjectiles;
		
	
	// Operationalize Sprite Movement
    var completedMoves = [];

    for (let [key, value] of activeMoves) {
	  if (executeMoveStep(value)) {
	  	  completedMoves.push(key);
	  }
    }

    for (i = 0; i < completedMoves.length; i++) {
      activeMoves.delete(completedMoves[i]);
    }

	if (player1Notification!==null) {
		player1Notification.countdown = player1Notification.countdown-1;
		if (player1Notification.countdown<=0) {
			g.stage.removeChild(player1Notification);
			g.stage.removeChild(player1NotificationText);
			player1Notification = null;
			player1NotificationText = null;
		}
	}
	
	if (player2Notification!==null) {
		player2Notification.countdown = player2Notification.countdown-1;
		if (player2Notification.countdown<=0) {
			g.stage.removeChild(player2Notification);
			g.stage.removeChild(player2NotificationText);
			player2Notification = null;
			player2NotificationText = null;
		}
	}
	
	// Remove the card preview after the pre-determined time
	if (cardPreview!=null) {
		
		if (prevewCountdown<=0) {
			g.stage.removeChild(cardPreview);
			cardPreview = null;
		}
		prevewCountdown = prevewCountdown -1;
		
	}
	
	// tidy up animations that have finished playing
	for (i = 0; i < playingEffects.length; i++) {
		if (playingEffects[i].killCountdown<=0) {
			g.stage.removeChild(playingEffects[i]);
			playingEffects.splice(i, 1);
			break;
		}
		playingEffects[i].killCountdown = playingEffects[i].killCountdown-1;
    }

	updateOverlayAnimations();
	ensureOverlayZOrder();
	

	sinceLastHeartbeat = sinceLastHeartbeat + 1;
	if (sinceLastHeartbeat >= 600) {
		if (ws && ws.readyState === WebSocket.OPEN) {
			ws.send(JSON.stringify({
    			messagetype: "heartbeat"
        	}));
		}
        sinceLastHeartbeat = 0;
	}
    				
  }

  

}
