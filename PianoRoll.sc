/*
	PianoRoll - a piano roll for SuperCollider
	TODO:
	* make sure ySpec can be \freq, \instrument, etc.
*/

PianoRoll : SCViewHolder {
	var <bounds;
	var <translate;
	var <grid=true;
	var <>selectedStrokeColor;
	var <>highlightedStrokeColor;
	var <ySpec=\midinote;
	var <vSize = 20; // size of each row in pixels
	var <vquant;
	var <beatSize = 80; // size of each beat in pixels
	var <beatQuant = 0.25; // x quantizing
	var <sidebarWidth = 40, <topbarHeight = 20;
	
	var <>selected; // list of selected notes
	var <>highlighted; // list of highlighted notes
	var clickedOn, mouseDownPoint, mouseDownButton, dragAction=nil; // mouse actions context info
	var selectionRect, ghosts;
	var tview, uv;
	var <sequence;
	var newNoteSustain=\last;
	var <>messagequeue, <>keymap;

	*initClass {
		Spec.add(\midinote, [0, 127, \lin, 1, 60]); // i have no idea why the default step for \midinote is 0...
	}
	
	*new {
		| parent bounds sequence |
		^super.new.init(parent, bounds, sequence);
	}
	
	init {
		| parent argbounds argsequence |
		bounds = argbounds ?? {Rect(0, 0, 500, 500)};
        bounds = bounds.asRect;
		sequence = argsequence ?? { Sequence.new };
		vquant = 1; // vertical quant - remove this? - FIX
		ySpec = ySpec.asSpec;//ControlSpec(0, 127, \lin, 1, 64);
		translate = Point(0, 0);
		selected = List.new;
		selectedStrokeColor = Color.blue;
		highlighted = List.new;
		highlightedStrokeColor = Color.green;
		messagequeue = [];
		uv = UserView(parent, bounds).background_(Color.black);
		tview = UserView(parent, bounds).acceptsMouse_(false);
		view = UserView(parent, bounds).layout_(StackLayout(tview, uv).mode_(\stackAll));
		tview.drawFunc_({
			| view |
			Pen.translate(sidebarWidth+translate.x, topbarHeight+translate.y);
			if(selectionRect.notNil, {
				Pen.fillColor_(Color.gray(0.5, 0.2));
				Pen.strokeColor_(Color.gray(0.5, 0.4));
				Pen.addRect(selectionRect);
				Pen.draw(3);
			});
			if(ghosts.notNil, {
				Pen.color_(Color.red(1, 0.5));
				ghosts.do({
					| note n |
					if(note.notNil, {
						var rect = this.noteToRect(note);
						Pen.addRect(rect);
						Pen.draw(3);
					});
				});
			});
		});
		uv.drawFunc_({
			| view |
			if(grid, { // draw grid lines
				var numlines = ((view.bounds.width)/(beatSize*beatQuant));
				numlines.floor.do({ // vertical lines
					| n |
					var xpos = sidebarWidth+(n*(beatSize*beatQuant));
					if(((n*(beatSize*beatQuant))-translate.x)%beatSize == 0, {
						Pen.color_(Color.gray(1, 0.5));
					}, {
						Pen.color_(Color.gray(1, 0.125));
					});
					Pen.line(Point(xpos, translate.y.max(0)), Point(xpos, view.bounds.height));
					Pen.stroke;
				});
				Pen.color_(Color.gray(1, 0.125));
				((view.bounds.height-(translate.y).min(0))/vSize).floor.do { // horizontal lines
					| n |
					var ypos = translate.y+((1+n)*vSize);
					Pen.line(Point(translate.x.max(0), ypos), Point(view.bounds.width, ypos));
				};
				Pen.stroke;
			});
			Pen.translate(sidebarWidth+translate.x, topbarHeight+translate.y);
			sequence.rawList.do {
				| note n |
				if(note.notNil, {
					var rect = this.noteToRect(note);
					Pen.fillColor_(case(
						{ this.isSelected(note) }, {
							selectedStrokeColor;
						},
						{ this.isHighlighted(note) }, {
							highlightedStrokeColor;
						},
						true, {
							Color.red;
						},
					));
					Pen.strokeColor_(Color.black);
					Pen.addRect(rect);
					Pen.draw(3);
					Pen.color_(Color.black);
					Pen.stringCenteredIn(note[\midinote].asString, rect);
					Pen.stroke;
				});
			};
			Pen.translate((sidebarWidth+translate.x).neg, (topbarHeight+translate.y).neg);
			if(topbarHeight > 0, {
				var hOffset = (translate.x.abs/beatSize).max(0).floor;
				Pen.color_(Color.red(0.5));
				Pen.addRect(Rect(0, 0, view.bounds.width, topbarHeight));
				Pen.fill;
				(view.bounds.width/beatSize).ceil.do({
					| n |
					var xpos = ((n+hOffset)*beatSize)+sidebarWidth+translate.x;
					var num = (n+hOffset);
					Pen.color_(if(num < this.sequence.dur, Color.black, Color.grey(0.5))); // show dur of sequence
					Pen.stringCenteredIn(num.asString, Rect(xpos, 0, beatSize, topbarHeight));
				});
				Pen.stroke;
			});
			if(sidebarWidth > 0, {
				var vOffset = (translate.y/vSize).min(0).floor.abs;
				var sArray = (ySpec.minval, if(ySpec.step==0, ySpec.guessNumberStep, ySpec.step) .. ySpec.maxval);
				Pen.color_(Color.red(0.5));
				Pen.addRect(Rect(0, 0, sidebarWidth, view.bounds.height));
				Pen.fill;
				Pen.color_(Color.black);
				(view.bounds.height/vSize).floor.do({ // numbers
					| n |
					var ypos = (n*vSize)+topbarHeight;
					Pen.stringCenteredIn((ySpec.maxval-(vOffset+n)).asString, Rect(0, ypos, sidebarWidth, vSize));
				});
				Pen.stroke;
			});
			// messages
			messagequeue = messagequeue.select({
				| item |
				(item[\time]-(Date.localtime.rawSeconds-item[\added]))>0;
			});
			messagequeue.select({|x|x[\text].notNil or: { x[\obj].notNil }}).reverse.do({
				| item num |
				var text = if(item[\text].notNil, {
					item[\text];
				}, {
					item[\obj].cs;
				});
				Pen.stringAtPoint(text, (sidebarWidth+5)@(view.bounds.height-(15*(num+2))-10), Font(), item[\color]??Color.white);
			});
		});
		keymap = Keymap((
			'C-1': \zoomIn,
			'C-3': \zoomOut,
			'i': \halveQuant,
			'o': \doubleQuant,
			\space: \playPause,
			'S-space': Message(this, \playPause, [nil, nil, true]),
			\delete: \deleteSelected,
			// \enter: {
			// 	| this |
			// 	this.edit(if(this.selected.size==0, nil, this.selected));
			// },
			['C-h', '?']: \help,
		));
		uv.keyDownAction_({
			| view char modifiers unicode keycode |
			var res = keymap.keyDown(Keymap.stringifyKey(modifiers, keycode));
			case(
				{ res.isKindOf(Function) }, {
					res.value(this);
				},
				{ res.isKindOf(Symbol) }, {
					Message(this, res, []).value;
				},
				{ res.isKindOf(Message) }, {
					res.value;
				},
			);
		});
		uv.mouseDownAction_({
			| view x y modifiers buttonNumber clickCount |
			case(
				{ x <= sidebarWidth and: { y <= topbarHeight } }, { // clicked in the top left bit
					if(clickCount == 2, {
					});
				},
				{ x <= sidebarWidth }, { // clicked on the sidebar
					// "sidebar".postln;
				},
				{ y <= topbarHeight }, { // clicked on the top bar
					if(buttonNumber == 1, { // right click to set dur...
						var beat = this.pointToBeat((x@y));
						this.sequence.dur_(if(beat < 1, nil, beat.round));
					});
				},
				true, { // clicked in the note area
					clickedOn = this.getAtPoint(x@y);
					mouseDownPoint = x@y;
					mouseDownButton = buttonNumber;
					switch(buttonNumber,
						0, {
							switch(clickCount,
								1, { // left click to select notes.
									if(clickedOn.size > 0, { // clicked on something
										var nrect = this.noteToRect(clickedOn[0]);
										dragAction = nil;
										if(( buttonNumber == 0 ) and: { (this.untranslatePoint(nrect.rightBottom) - (x@y)).x < 5 }, {
											dragAction = \resize;
										});
									});
									if(modifiers.isShift, {
										clickedOn.do({
											| item |
											if(this.isSelected(item), {
												this.deselect(item);
											}, {
												this.select(item);
											});
										});
									}, {
										if(clickedOn.size == 0, {
											this.deselectAll;
										}, {
											if(this.isSelected(clickedOn[0]).not, {
												this.deselectAll;
											});
											if(selected.size == 0, {
												clickedOn.do({
													| item |
													this.select(item);
												});
											});
										});
									});
								},
								2, { // double left-click to make a note
									if((clickedOn.size > 0) and: { selected.size == 1 }, {
										selected[0].deepCopy.play;
										this.message("Event #"++this.sequence.list.indexOf(selected[0]).asString);
									}, {
										this.add((
											midinote: (this.pointToY(x@y)+(vquant/2)).round(vquant),
											sustain: this.newNoteSustain,
											beat: (this.pointToBeat(x@y)-(beatQuant/2)).round(beatQuant),
										));
									});
								},
							);
						},
						1, {
							this.getAtPoint(x@y).do({
								| item |
								this.delete(item);
							});
						},
					);
				},
			);
			this.refresh;
		});
		uv.mouseMoveAction_({
			| uvw x y modifiers |
			switch(mouseDownButton,
				0, {
					if(clickedOn.size == 0, { // draw the selection rect if we didn't click on anything.
						var untranslatedRect = Rect.fromPoints(mouseDownPoint, x@y);
						selectionRect = Rect.fromPoints(
							this.translatePoint(mouseDownPoint),
							this.translatePoint(x@y),
						);
					}, { // if we clicked on something, move or resize all selected notes
						if(dragAction == \resize, { // FIX: just find the % of the resized note and apply it to all notes \sustain and \beat
							var drag, c_beats, start, end, oldsize, newsize;
							drag = ((x - mouseDownPoint.x)/beatSize).round(beatQuant);
							c_beats = selected.collect(_[\beat]);
							start = c_beats.reduce(\min);
							end = selected.sortBy(\beat);
							end = end.reject({
								| e |
								e[\beat] < end.last[\beat];
							});
							end = end.collect(_[\sustain]).reduce(\max) + end[0][\beat];
							oldsize = end - start;
							newsize = (end + drag) - start;
							ghosts = selected.deepCopy;
							ghosts.do({
								| item |
								item[\sustain] = (item[\sustain] * (newsize/oldsize));
								item[\beat] = ((item[\beat]-start)*(newsize/oldsize))+start;
							});
						}, {
							ghosts = selected.deepCopy;
							ghosts.do({
								| item |
								var diff = ((x@y) - mouseDownPoint);
								item[\beat] = item[\beat] + (diff.x/beatSize).round(beatQuant);
								item[\midinote] = item[\midinote]?60 - (diff.y/vSize).round;
							});
						});
					});
					tview.refresh;
				},
				1, {
					this.getAtPoint(x@y).do({
						| item |
						this.delete(item);
					});
					uv.refresh;
				},
			);
		});
		uv.mouseUpAction_({
			| uvw x y modifiers |
			if(selectionRect.notNil, { // select items in the rect on mouse up
				var untranslatedRect = Rect.fromPoints(
					this.untranslatePoint(selectionRect.leftTop),
					this.untranslatePoint(selectionRect.rightBottom),
				);
				var items = this.getInRect(untranslatedRect);
				items.do({
					| item |
					this.select(item);
				});
				selectionRect = nil;
			});
			if(ghosts.notNil, {
				var os = selected.deepCopy;
				var gh = ghosts.deepCopy;
				if(modifiers.isAlt.not, {
					os.do({
						| item |
						this.delete(item);
					});
					this.deselectAll;
				});
				gh.do({
					| item |
					this.add(item);
					this.select(item);
				});
				ghosts = nil;
			});
			this.refresh;
		});
		uv.mouseWheelAction_({
			| view x y modifiers xDelta yDelta |
			if(modifiers.isCtrl, { // zoom
				beatSize = beatSize + (yDelta.sign * 10);
				this.refresh;
			}, {
				var newtrans = this.translate;
				newtrans.x = (newtrans.x + (xDelta.sign*(beatSize*beatQuant)));
				newtrans.y = (newtrans.y + (yDelta.sign*vSize));
				this.translate_(newtrans);
			});
		});
		this.centerOn(\midinote.asSpec.default);
	}
	prNoteCompare {
		| event1 event2 |
		// FIX - will need to consider other stuff instead of \midinote when other ySpecs are supported by PianoRoll.
		^(event1[\beat] == event2[\beat] and: { event1[\midinote] == event2[\midinote] });
	}
	message {
		| obj time raw=false |
		if(raw, {
			messagequeue = messagequeue ++ [(obj:obj, time:time?5, added:Date.localtime.rawSeconds)];
		}, {
			if(time.isNil, {
				time = 0;
				if(obj.isKindOf(String), {
					time = obj.split($\n).size.max(5);
				}, {
					time = 5;
				});
			});
			if(obj.notNil, {
				var ctime = Date.localtime.rawSeconds;
				messagequeue = messagequeue ++ if(obj.isKindOf(String), {
					obj.split($\n).collect({
						| obji |
						[(text:obji, time:time, added:ctime)];
					}).reduce('++');
				}, {
					[(obj:obj, time:time, added:ctime)];
				});
			});
		});
		this.refresh;
	}
	bounds_ {
		| bounds |
		bounds = bounds;
		view.bounds = bounds;
		this.refresh;
	}
	newNoteSustain {
		if(newNoteSustain==\last, {
			if(this.sequence.list.size == 0, {
				^1;
			}, {
				^this.sequence.rawList.last[\sustain];
			});
		}, {
			^newNoteSustain;
		});
	}
	newNoteSustain_ {
		| sustain |
		newNoteSustain = if(sustain.isKindOf(Number) or: { sustain == \last }, sustain, 1);
	}
	add {
		| note |
		sequence.add(note);
		this.refresh;
	}
	delete {
		| obj |
		this.deselect(obj);
		sequence.remove(obj);
		this.refresh;
	}
	del {
		| obj |
		^this.delete(obj);
	}
	zoomIn {
		beatSize = beatSize + 10;
		this.refresh;
	}
	zoomOut {
		beatSize = beatSize - 10;
		this.refresh;
	}
	halveQuant {
		this.beatQuant_((this.beatQuant/2).clip(1/2048, 8));
	}
	doubleQuant {
		this.beatQuant_((this.beatQuant*2).clip(1/2048, 8));
	}
	deleteSelected {
		var csel = this.selected.deepCopy;
		csel.do({
			| item |
			this.delete(item);
		});
		this.deselectAll;
	}
	edit {
		| obj |
		if(obj.isNil, {
			// edit the sequence
		}, {
			// edit the selected notes
		});
	}
	sequence_ {
		| seq |
		sequence = seq ?? { Sequence.new; };
		this.refresh;
	}
	getInRect { // returns the objects that are contained inside the provided Rect. don't provide a translated rect.
		| rect |
		var topYVal = this.pointToY(rect.top), botYVal = this.pointToY(rect.top+rect.height);
		^sequence.eventsIn(this.pointToBeat(rect.left-this.translate.x), this.pointToBeat(rect.left+rect.width-this.translate.x))
		.select({
			| event |
			((event[\midinote]?60 >= botYVal) and: { event[\midinote]?60 <= topYVal });
		});
	}
	getAtPoint { // returns the objects that contain the provided Point.
		| point |
		var beat = this.pointToBeat(point);
		var yVal = this.pointToY(point).ceil;
		// point.postcs;
		^sequence.rawList.select({
			| event |
			var end = event[\beat] + event[\sustain];
			((event[\beat] <= beat) and: { beat < end } and: { event[\midinote]?60 == yVal });
		});
	}
	isSelected {
		| obj |
		selected.do({
			| item |
			if(this.prNoteCompare(item, obj), {
				^true;
			});
		});
		^false;
	}
	select {
		| obj |
		if(this.isSelected(obj).not, { // FIX: don't select things that don't exist
			selected = selected.add(obj);
		});
	}
	deselect {
		| obj |
		selected = selected.reject(this.prNoteCompare(_, obj));
	}
	deselectAll {
		selected = [];
	}
	isHighlighted {
		| obj |
		highlighted.do({
			| item |
			if(this.prNoteCompare(item, obj), {
				^true;
			});
		});
		^false;
	}
	highlight {
		| obj |
		if(this.isHighlighted(obj).not, { // FIX: don't select things that don't exist
			highlighted = highlighted.add(obj);
		});
		this.refresh;
	}
	unhighlight {
		| obj |
		highlighted = highlighted.reject(this.prNoteCompare(_, obj));
		this.refresh;
	}
	unhighlightAll {
		highlighted = [];
	}
	notePlay {
		| event |
		if(event.notNil, {
			{this.highlight(event);}.defer(0);
			{this.unhighlight(event)}.defer(event[\dur]*(1/TempoClock.default.tempo));
		});
	}
	pointToBeat { // return the "beat" at the point. must be a non-translated point. - FIX
		| point | // should also accept numbers instead of just Points.
		var ix = if(point.isKindOf(Point), { point.x }, point) - sidebarWidth;
		^(ix/beatSize);
	}
	pointToY { // return the y value at the point. must be a non-translated point. - FIX
		| point | // should also accept numbers instead of just Points.
		var iy = if(point.isKindOf(Point), { point.y }, point) - topbarHeight;
		^(ySpec.maxval-((iy-translate.y)/vSize));
	}
	beatToX { // return the (non-translated) x-position of the provided beat.
		| beat |
		^(beat*(beatSize));
	}
	noteToY { // return the (non-translated) (top) y-position of the provided 'midinote'.
		| note |
		^(vSize*(ySpec.maxval-note));
	}
	noteToRect {
		| note |
		^Rect(
			this.beatToX(note[\beat]),
			this.noteToY(note.use({ \midinote.envirGet.value; })?60),
			(note.use({\sustain.envirGet.value;}))*beatSize, // FIX: use event.use{ \sustain.envirGet } or w/e instead
			vSize
		);
	}
	translate_ { // translation of the piano roll grid.
		| point |
		translate = Point(
			point.x.min(0),
			point.y.round(vSize).min(0).max(-1*((vSize*(ySpec.range+1))-(view.bounds.height-topbarHeight))),
		);
		this.refresh;
	}
	translatePoint { // translates a point on the view to a point in the piano roll (i.e. 0,0 on piano roll is actually 40,-940 on the whole view)
		| point |
		^Point(point.x-translate.x-sidebarWidth, point.y-translate.y-topbarHeight);
	}
	untranslatePoint {
		| point |
		^Point(point.x+translate.x+sidebarWidth, point.y+translate.y+topbarHeight);
	}
	grid_ {
		| bool |
		grid = bool;
		this.refresh;
	}
	sidebarWidth_ {
		| width |
		sidebarWidth = width;
		this.refresh;
	}
	ySpec_ {
		| spec |
		ySpec = spec.asSpec;
		this.refresh;
	}
	vSize_ {
		| size |
		vSize = size;
		this.refresh;
	}
	beatSize_ {
		| size |
		beatSize = size;
		this.refresh;
	}
	beatQuant_ {
		| quant |
		beatQuant = quant;
		this.message("beatQuant set to" + quant.asString);
		this.refresh;
	}
	centerOn { // FIX
		| num |
		var numRows = view.bounds.height/vSize;
		this.topOn(num+(numRows/2));
	}
	topOn { // make the number the top row in the view
		| num |
		this.translate = Point(this.translate.x, -1*vSize*(ySpec.maxval-num));
	}
	isPlaying {
		^Pdef(\__pianoRoll).hasEnded.not;
	}
	play {
		| start end loop=false |
		var pat = Plazy({
			var pr = this;
			var pattern = pr.sequence.asPattern;
			var pbeats = pr.sequence.dur;
			Pchain(
				Pbind(\__, Pfunc({
					| e |
					pr.notePlay(e);
				})),
				Psync(pattern, pbeats, pbeats),
			);
		});
		Pdef(\__pianoRoll, if(loop, Pn(pat), pat));
		Pdef(\__pianoRoll).play;
	}
	stop {
		Pdef(\__pianoRoll).stop;
	}
	playPause {
		| start end loop=false |
		if(this.isPlaying, {
			this.stop;
		}, {
			this.play(start, end, loop);
		});
	}
	refresh {
		view.refresh;
	}
	help {
		this.keymap.helpInfo.collect({
			| info |
			info[0] ++ " -- " ++ info[1];
		}).do({
			| msg |
			this.message(msg);
		});
	}
}

/*
	+ Pattern {
	pianoRoll {
	
	}
	}
*/