/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.util;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.Preconditions;

public enum Direction {
	
	UP    ( 0, 1, 0),
	DOWN  ( 0,-1, 0),
	NORTH ( 0, 0,-1),
	SOUTH ( 0, 0, 1),
	WEST  (-1, 0, 0),
	EAST  ( 1, 0, 0);
	
	private Vector3i dir;
	
	private Direction(int x, int y, int z) {
		this.dir = new Vector3i(x, y, z);
	}
	
	public Vector3i toVector(){
		return dir;
	}
	
	public static Direction fromString(String name){
		Preconditions.checkNotNull(name);
		
		return valueOf(name.toUpperCase());
	}
}
