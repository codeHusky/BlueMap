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
package de.bluecolored.bluemap.api;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Represents a block in a {@link World}<br>
 * It is important that {@link #hashCode} and {@link #equals} are implemented correctly, for the caching to work properly.<br>
 * <br>
 * <i>The implementation of this class has to be thread-save!</i><br>
 */
public abstract class BlockState {

	private boolean hashed;
	private int hash;
	
	public BlockState() {
		this.hashed = false;
		this.hash = 0;
	}
	
	/**
	 * The name of the resource-file without the filetype that represents this block-state (found in mineceraft in assets/minecraft/blockstates).<br>
	 * <br>
	 * <i>All id's are normalized by removing _ characters and making it lowercase, so those differences to the correct filename can be safely ignored, if that helps in the implementation.</i><br>
	 */
	public abstract String getResourceId();
	
	/**
	 * A map of all properties of this block.<br>
	 * Should always return the same map, the map is unmodifiable.<br>
	 * <br>
	 * <i>All id's are normalized by removing _ characters and making it lowercase, so those differences to the correct property can be safely ignored, if that helps in the implementation.</i><br>
	 * <br>
	 * For Example:<br>
	 * <code>
	 * facing = east<br>
	 * half = bottom<br>
	 * </code>
	 */
	public abstract Map<String, String> getProperties();
	
	public final boolean checkVariantCondition(String condition){
		if (condition.isEmpty() || condition.equals("normal")) return true;

		Map<String, String> blockProperties = getProperties();
		String[] conditions = condition.split(",");
		for (String c : conditions){
			String[] kv = c.split("=", 2);
			String key = kv[0];
			String value = kv[1];
			
			if (!value.equals(blockProperties.get(key))){
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof BlockState)) return false;
		BlockState b = (BlockState) obj;
		if (!Objects.equals(getResourceId(), b.getResourceId())) return false;
		if (!Objects.equals(getProperties(), b.getProperties())) return false;
		return true;
	}
	
	@Override
	public int hashCode() {
		if (!hashed){
			hash = Objects.hash( getResourceId(), getProperties() );
			hashed = true;
		}
		
		return hash;
	}
	
	@Override
	public String toString() {
		StringJoiner sj = new StringJoiner(",");
		for (Entry<String, String> e : getProperties().entrySet()){
			sj.add(e.getKey() + "=" + e.getValue());
		}
		
		return getResourceId() + "[" + sj.toString() + "]";
	}
	
}
