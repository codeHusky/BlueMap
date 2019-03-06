function BlueMap(element, dataRoot) {
	let scope = this;

	this.element = element;
	this.dataRoot = dataRoot;

	this.fileLoader = new THREE.FileLoader();
	this.blobLoader = new THREE.FileLoader();
	this.blobLoader.setResponseType("blob");
	this.bufferGeometryLoader = new THREE.BufferGeometryLoader();

	this.initStage();
	this.controls = new BlueMap.Controls(this.camera, this.element, this.hiresScene);

	this.loadSettings(function () {
		this.lowresTileManager = new BlueMap.TileManager(
			this,
			this.settings["lowres"]["viewDistance"],
			this.loadLowresTile,
			this.lowresScene,
			this.settings["lowres"]["tileSize"],
			{x: 0, z: 0}
		);

		this.hiresTileManager = new BlueMap.TileManager(
			this,
			this.settings["hires"]["viewDistance"],
			this.loadHiresTile,
			this.hiresScene,
			this.settings["hires"]["tileSize"],
			{x: 0, z: 0}
		);

		this.loadHiresMaterial(function () {
			this.loadLowresMaterial(function () {
				this.start();
			});
		});
	});
}

BlueMap.prototype.start = function () {
	$("#loading").remove();

	this.update();
	this.render();

	this.lowresTileManager.update();
	this.hiresTileManager.update();
};

BlueMap.prototype.update = function () {
	let scope = this;
	setTimeout(function () {
		scope.update()
	}, 1000);

	this.lowresTileManager.setPosition(this.controls.targetPosition);
	this.hiresTileManager.setPosition(this.controls.targetPosition);
};

BlueMap.prototype.render = function () {
	let scope = this;
	requestAnimationFrame(function () {
		scope.render()
	});

	if (this.controls.update()) this.updateFrame = true;

	if (!this.updateFrame) return;
	this.updateFrame = false;

	this.renderer.clear();
	this.renderer.render(this.lowresScene, this.camera, this.renderer.getRenderTarget(), false);
	if (this.camera.position.y < 400) {
		this.renderer.clearDepth();
		this.renderer.render(this.hiresScene, this.camera, this.renderer.getRenderTarget(), false);
	}
};

BlueMap.prototype.handleContainerResize = function () {
	this.camera.aspect = this.element.clientWidth / this.element.clientHeight;
	this.camera.updateProjectionMatrix();

	let resolutionMultiplier = 1;

	this.renderer.setSize(this.element.clientWidth * resolutionMultiplier, this.element.clientHeight * resolutionMultiplier);
	$(this.renderer.domElement)
		.css("width", this.element.clientWidth)
		.css("height", this.element.clientHeight);

	this.updateFrame = true;
};

BlueMap.prototype.loadSettings = function (callback) {
	let scope = this;

	this.fileLoader.load(this.dataRoot + "settings.json", function (settings) {
		settings = JSON.parse(settings);
		
		scope.maps = Object.keys(settings);
		scope.map = scope.maps[0];
		
		scope.settings = settings[scope.map];
		callback.call(scope);
	});
};

BlueMap.prototype.initStage = function () {
	let scope = this;

	this.updateFrame = true;

	this.renderer = new THREE.WebGLRenderer({
		alpha: true,
		antialias: true,
		sortObjects: false,
		preserveDrawingBuffer: true,
	});
	this.renderer.autoClear = false;

	this.camera = new THREE.PerspectiveCamera(75, this.element.scrollWidth / this.element.scrollHeight, 0.1, 10000);
	this.camera.updateProjectionMatrix();

	this.lowresScene = new THREE.Scene();
	this.lowresScene.ambient = new THREE.AmbientLight(0xffffff, 0.6);
	this.lowresScene.add(this.lowresScene.ambient);
	this.lowresScene.sunLight = new THREE.DirectionalLight(0xccccbb, 0.7);
	this.lowresScene.sunLight.position.set(1, 5, 3);
	this.lowresScene.add(this.lowresScene.sunLight);

	this.hiresScene = new THREE.Scene();
	this.hiresScene.ambient = new THREE.AmbientLight(0xffffff, 1);
	this.hiresScene.add(this.hiresScene.ambient);
	this.hiresScene.sunLight = new THREE.DirectionalLight(0xccccbb, 0.2);
	this.hiresScene.sunLight.position.set(1, 5, 3);
	this.hiresScene.add(this.hiresScene.sunLight);
	//this.hiresScene.moonLight = new THREE.DirectionalLight(0xccccbb, 0.1);
	//this.hiresScene.moonLight.position.set(-1, -5, -3);
	//this.hiresScene.add(this.hiresScene.moonLight);

	this.element.append(this.renderer.domElement);
	this.handleContainerResize();

	$(window).resize(function () {
		scope.handleContainerResize()
	});
};

BlueMap.prototype.loadHiresMaterial = function (callback) {
	let scope = this;

	this.fileLoader.load(this.dataRoot + "textures.json", function (textures) {
		textures = JSON.parse(textures);

		let materials = [];
		for (let i = 0; i < textures["textures"].length; i++) {
			let t = textures["textures"][i];

			let material = new THREE.MeshLambertMaterial({
				transparent: t["transparent"],
				alphaTest: 0.01,
				depthWrite: true,
				depthTest: true,
				blending: THREE.NormalBlending,
				vertexColors: THREE.VertexColors,
				side: THREE.FrontSide,
				wireframe: false
			});

			let texture = new THREE.Texture();
			texture.image = BlueMap.utils.stringToImage(t["texture"]);

			texture.premultiplyAlpha = false;
			texture.generateMipmaps = false;
			texture.magFilter = THREE.NearestFilter;
			texture.minFilter = THREE.NearestFilter;
			texture.wrapS = THREE.RepeatWrapping;
			texture.wrapT = THREE.RepeatWrapping;
			texture.flipY = false;
			texture.needsUpdate = true;
			texture.flatShading = true;

			material.map = texture;
			material.needsUpdate = true;

			materials[i] = material;
		}

		scope.hiresMaterial = materials;

		callback.call(scope);
	});
};

BlueMap.prototype.loadLowresMaterial = function (callback) {
	this.lowresMaterial = new THREE.MeshLambertMaterial({
		transparent: false,
		//alphaTest: 0.01,
		depthWrite: true,
		depthTest: true,
		vertexColors: THREE.VertexColors,
		side: THREE.FrontSide,
		wireframe: false
	});

	callback.call(this);
};

BlueMap.prototype.loadHiresTile = function (tileX, tileZ, callback, onError) {
	let scope = this;

	let path = this.dataRoot + "hires/" + this.map + "/";
	path += BlueMap.utils.pathFromCoords(tileX, tileZ);
	path += ".json";


	this.bufferGeometryLoader.load(path, function (geometry) {
		let object = new THREE.Mesh(geometry, scope.hiresMaterial);

		let tileSize = scope.settings.hires["tileSize"];
		let translate = scope.settings.hires["translate"];
		let scale = scope.settings.hires["scale"];
		object.position.set(tileX * tileSize.x + translate.x, 0, tileZ * tileSize.z + translate.z);
		object.scale.set(scale.x, 1, scale.z);

		callback.call(scope, object);
	}, function () {

	}, function (error) {
		onError.call(scope, error);
	});
};

BlueMap.prototype.loadLowresTile = function (tileX, tileZ, callback, onError) {
	let scope = this;

	let path = this.dataRoot + "lowres/" + this.map + "/";
	path += BlueMap.utils.pathFromCoords(tileX, tileZ);
	path += ".json";

	this.bufferGeometryLoader.load(path, function (geometry) {
		let object = new THREE.Mesh(geometry, scope.lowresMaterial);

		let tileSize = scope.settings.lowres["tileSize"];
		let translate = scope.settings.lowres["translate"];
		let scale = scope.settings.lowres["scale"];
		object.position.set(tileX * tileSize.x + translate.x, 0, tileZ * tileSize.z + translate.z);
		object.scale.set(scale.x, 1, scale.z);

		callback.call(scope, object);
	}, function () {

	}, function (error) {
		onError.call(scope, error);
	});
};


// ###### TileManager ######
BlueMap.TileManager = function (blueMap, viewDistance, tileLoader, scene, tileSize, position) {
	this.blueMap = blueMap;
	this.viewDistance = viewDistance;
	this.tileLoader = tileLoader;
	this.scene = scene;
	this.tileSize = new THREE.Vector2(tileSize.x, tileSize.z);

	this.tile = new THREE.Vector2(position.x, position.z);
	this.lastTile = this.tile.clone();

	this.currentlyLoading = 0;
	this.updateTimeout = null;

	this.tiles = {};
};

BlueMap.TileManager.prototype.setPosition = function (center) {
	this.tile.set(center.x, center.z).divide(this.tileSize).floor();

	if (!this.tile.equals(this.lastTile)) {
		this.update();
		this.lastTile.copy(this.tile);
	}
};

BlueMap.TileManager.prototype.update = function () {

	//free a loader so if there was an error loading a tile we don't get stuck forever with the blocked loading process
	this.currentlyLoading--;
	if (this.currentlyLoading < 0) this.currentlyLoading = 0;

	this.removeFarTiles();
	this.loadCloseTiles();
};

BlueMap.TileManager.prototype.removeFarTiles = function () {
	let keys = Object.keys(this.tiles);
	for (let i = 0; i < keys.length; i++) {
		if (!this.tiles.hasOwnProperty(keys[i])) continue;

		let tile = this.tiles[keys[i]];

		let vd = this.viewDistance;

		if (
			tile.x + vd < this.tile.x ||
			tile.x - vd > this.tile.x ||
			tile.z + vd < this.tile.y ||
			tile.z - vd > this.tile.y
		) {
			tile.disposeModel();
			delete this.tiles[keys[i]];
		}
	}
};

BlueMap.TileManager.prototype.loadCloseTiles = function () {
	let scope = this;

	if (this.currentlyLoading < 8) {
		if (!this.loadNextTile()) return;
	}

	if (this.updateTimeout) clearTimeout(this.updateTimeout);
	this.updateTimeout = setTimeout(function () {
		scope.loadCloseTiles()
	}, 0);
};

BlueMap.TileManager.prototype.loadNextTile = function () {
	let x = 0;
	let z = 0;
	let d = 1;
	let m = 1;

	while (m < this.viewDistance * 2) {
		while (2 * x * d < m) {
			if (this.tryLoadTile(this.tile.x + x, this.tile.y + z)) return true;
			x = x + d;
		}
		while (2 * z * d < m) {
			if (this.tryLoadTile(this.tile.x + x, this.tile.y + z)) return true;
			z = z + d;
		}
		d = -1 * d;
		m = m + 1;
	}

	return false;
};

BlueMap.TileManager.prototype.tryLoadTile = function (x, z) {
	let scope = this;

	let tileHash = BlueMap.utils.hashTile(x, z);

	let tile = this.tiles[tileHash];
	if (tile !== undefined) return false;

	tile = new BlueMap.Tile(this.scene, x, z);
	tile.isLoading = true;

	this.currentlyLoading++;

	this.tiles[tileHash] = tile;

	this.tileLoader.call(this.blueMap, x, z, function (model) {
		tile.isLoading = false;

		if (tile.disposed) {
			model.geometry.dispose();
			tile.disposeModel();
			delete scope.tiles[tileHash];
			return;
		}

		scope.tiles[tileHash] = tile;
		tile.setModel(model);

		scope.blueMap.updateFrame = true;

		scope.currentlyLoading--;
		if (scope.currentlyLoading < 0) scope.currentlyLoading = 0;
	}, function (error) {
		tile.isLoading = false;
		tile.disposeModel();

		scope.currentlyLoading--;

		console.log("Failed to load tile: ", x, z);
	});

	return true;
};


// ###### Tile ######
BlueMap.Tile = function (scene, x, z) {
	this.scene = scene;
	this.x = x;
	this.z = z;

	this.isLoading = false;
	this.disposed = false;

	this.model = null;
};

BlueMap.Tile.prototype.setModel = function (model) {
	this.disposeModel();

	if (model) {
		this.model = model;
		this.scene.add(model);

		console.log("Added tile:", this.x, this.z);
	}
};

BlueMap.Tile.prototype.disposeModel = function () {
	this.disposed = true;

	if (this.model) {
		this.scene.remove(this.model);
		this.model.geometry.dispose();
		delete this.model;

		console.log("Removed tile:", this.x, this.z);
	}
};


// ###### Controls ######

/**
 * targetHeightScene and cameraHeightScene are scenes of objects that are checked via raycasting for a height for the target and the camera
 */
BlueMap.Controls = function (camera, element, heightScene) {
	let scope = this;

	this.settings = {
		zoom: {
			min: 10,
			max: 2000,
			speed: 1.5,
			smooth: 0.2,
		},
		move: {
			speed: 1.75,
			smooth: 0.3,
			smoothY: 0.075,
		},
		tilt: {
			max: Math.PI / 2.1,
			speed: 1.5,
			smooth: 0.3,
		},
		rotate: {
			speed: 1.5,
			smooth: 0.3,
		}
	};

	this.camera = camera;
	this.element = element;
	this.heightScene = heightScene;
	this.minHeight = 0;

	this.raycaster = new THREE.Raycaster();
	this.rayDirection = new THREE.Vector3(0, -1, 0);
	
	this.position = new THREE.Vector3(0, 70, 0);
	this.targetPosition = new THREE.Vector3(0, 70, 0);

	this.distance = 5000;
	this.targetDistance = 1000;

	this.direction = 0;
	this.targetDirection = 0;

	this.angle = 0;
	this.targetAngle = 0;

	this.mouse = new THREE.Vector2(0, 0);
	this.lastMouse = new THREE.Vector2(0, 0);
	this.deltaMouse = new THREE.Vector2(0, 0);

	//variables used to calculate with (to prevent object creation every update)
	this.orbitRot = new THREE.Euler(0, 0, 0, "YXZ");
	this.cameraPosDelta = new THREE.Vector3(0, 0, 0);
	this.moveDelta = new THREE.Vector2(0, 0);

	this.KEYS = {
		LEFT: 37,
		UP: 38,
		RIGHT: 39,
		DOWN: 40,
		ORBIT: THREE.MOUSE.RIGHT,
		MOVE: THREE.MOUSE.LEFT
	};
	this.STATES = {
		NONE: -1,
		ORBIT: 0,
		MOVE: 1,
	};

	this.state = this.STATES.NONE;

	this.element.addEventListener('contextmenu', function (e) {
		e.preventDefault();
	}, false);
	this.element.addEventListener('mousedown', function (e) {
		scope.onMouseDown(e);
	}, false);
	document.addEventListener('mousemove', function (e) {
		scope.onMouseMove(e);
	}, false);
	document.addEventListener('mouseup', function (e) {
		scope.onMouseUp(e);
	}, false);
	this.element.addEventListener('wheel', function (e) {
		scope.onMouseWheel(e);
	}, false);

	this.camera.position.set(0, 1000, 0);
	this.camera.lookAt(this.position);
	this.camera.updateProjectionMatrix();
};

BlueMap.Controls.prototype.update = function () {
	this.updateMouseMoves();

	let changed = false;
	
	let zoomLerp = (this.distance - 100) / 200;
	if (zoomLerp < 0) zoomLerp = 0;
	if (zoomLerp > 1) zoomLerp = 1;
	this.targetPosition.y = 300 * zoomLerp + this.minHeight * (1 - zoomLerp);
	
	this.position.x += (this.targetPosition.x - this.position.x) * this.settings.move.smooth;
	this.position.y += (this.targetPosition.y - this.position.y) * this.settings.move.smoothY;
	this.position.z += (this.targetPosition.z - this.position.z) * this.settings.move.smooth;

	this.distance += (this.targetDistance - this.distance) * this.settings.zoom.smooth;

	let deltaDir = (this.targetDirection - this.direction) * this.settings.rotate.smooth;
	this.direction += deltaDir;
	changed = changed || Math.abs(deltaDir) > 0;

	let max = Math.min(this.settings.tilt.max, this.settings.tilt.max / (Math.pow(this.distance / 50, 0.5)));
	if (this.targetAngle < 0.01) this.targetAngle = 0.01;
	if (this.targetAngle > max) this.targetAngle = max;
	let deltaAngle = (this.targetAngle - this.angle) * this.settings.tilt.smooth;
	this.angle += deltaAngle;
	changed = changed || Math.abs(deltaAngle) > 0;

	let last = this.camera.position.x + this.camera.position.y + this.camera.position.z;
	this.orbitRot.set(this.angle, this.direction, 0);
	this.cameraPosDelta.set(0, this.distance, 0).applyEuler(this.orbitRot);
	
	this.camera.position.set(this.position.x + this.cameraPosDelta.x, this.position.y + this.cameraPosDelta.y, this.position.z + this.cameraPosDelta.z);
	let move = last - (this.camera.position.x + this.camera.position.y + this.camera.position.z);

	changed = changed || Math.abs(move) > 0.01;

	if (changed) {
		this.camera.lookAt(this.position);
		this.camera.updateProjectionMatrix();
		
		this.updateMinHeight();
	}

	return changed;
};

BlueMap.Controls.prototype.updateMinHeight = function(){
		//TODO: this can be performance-improved by only intersecting the correct tile?
		
		let rayStart = new THREE.Vector3(this.targetPosition.x, 300, this.targetPosition.z);
		this.raycaster.set(rayStart, this.rayDirection);
		this.raycaster.near = 1;
		this.raycaster.far = 300;
		let intersects = this.raycaster.intersectObjects(this.heightScene.children);
		if (intersects.length > 0){
			this.minHeight = intersects[0].point.y;
		}
		
		rayStart.set(this.camera.position.x, 300, this.camera.position.z);
		this.raycaster.set(rayStart, this.rayDirection);
		intersects.length = 0;
		intersects = this.raycaster.intersectObjects(this.heightScene.children);
		if (intersects.length > 0){
			if (intersects[0].point.y > this.minHeight){
				this.minHeight = intersects[0].point.y;
			}
		}
}

BlueMap.Controls.prototype.updateMouseMoves = function (e) {
	this.deltaMouse.set(this.lastMouse.x - this.mouse.x, this.lastMouse.y - this.mouse.y);

	if (this.state === this.STATES.MOVE) {
		if (this.deltaMouse.x === 0 && this.deltaMouse.y === 0) return;

		this.moveDelta.copy(this.deltaMouse);
		this.moveDelta.rotateAround(BlueMap.utils.Vector2.ZERO, -this.direction);

		this.targetPosition.set(
			this.targetPosition.x + (this.moveDelta.x * this.distance / this.element.clientHeight * this.settings.move.speed),
			this.targetPosition.y,
			this.targetPosition.z + (this.moveDelta.y * this.distance / this.element.clientHeight * this.settings.move.speed)
		);
	}

	if (this.state === this.STATES.ORBIT) {
		this.targetDirection += (this.deltaMouse.x / this.element.clientHeight * Math.PI);
		this.targetAngle += (this.deltaMouse.y / this.element.clientHeight * Math.PI);
	}

	this.lastMouse.copy(this.mouse);
};

BlueMap.Controls.prototype.onMouseWheel = function (e) {
	if (e.deltaY > 0) {
		this.targetDistance *= this.settings.zoom.speed;
	} else if (e.deltaY < 0) {
		this.targetDistance /= this.settings.zoom.speed;
	}

	if (this.targetDistance < this.settings.zoom.min) this.targetDistance = this.settings.zoom.min;
	if (this.targetDistance > this.settings.zoom.max) this.targetDistance = this.settings.zoom.max;
};

BlueMap.Controls.prototype.onMouseMove = function (e) {
	this.mouse.set(e.clientX, e.clientY);
};

BlueMap.Controls.prototype.onMouseDown = function (e) {
	if (this.state !== this.STATES.NONE) return;

	switch (e.button) {
		case this.KEYS.MOVE :
			this.state = this.STATES.MOVE;
			break;
		case this.KEYS.ORBIT :
			this.state = this.STATES.ORBIT;
			break;
	}
};

BlueMap.Controls.prototype.onMouseUp = function (e) {
	if (this.state === this.STATES.NONE) return;

	switch (e.button) {
		case this.KEYS.MOVE :
			if (this.state === this.STATES.MOVE) this.state = this.STATES.NONE;
			break;
		case this.KEYS.ORBIT :
			if (this.state === this.STATES.ORBIT) this.state = this.STATES.NONE;
			break;
	}
};


// ###### Utils ######
BlueMap.utils = {};

BlueMap.utils.stringToImage = function (string) {
	let image = document.createElementNS('http://www.w3.org/1999/xhtml', 'img');
	image.src = string;
	return image;
};

BlueMap.utils.pathFromCoords = function (x, z) {
	let path = "x";
	path += BlueMap.utils.splitNumberToPath(x);

	path += "z";
	path += BlueMap.utils.splitNumberToPath(z);

	path = path.substring(0, path.length - 1);

	return path;
};

BlueMap.utils.splitNumberToPath = function (num) {
	let path = "";

	if (num < 0) {
		num = -num;
		path += "-";
	}

	let s = num.toString();

	for (let i = 0; i < s.length; i++) {
		path += s.charAt(i) + "/";
	}

	return path;
};

BlueMap.utils.hashTile = function (x, z) {
	return "x" + x + "z" + z;
};

BlueMap.utils.Vector2 = {};
BlueMap.utils.Vector2.ZERO = new THREE.Vector2(0, 0);
BlueMap.utils.Vector3 = {};
BlueMap.utils.Vector3.ZERO = new THREE.Vector3(0, 0);