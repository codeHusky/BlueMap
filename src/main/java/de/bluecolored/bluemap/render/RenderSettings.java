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
package de.bluecolored.bluemap.render;

public interface RenderSettings {

	boolean isAmbientOcclusion();

	boolean isExcludeFacesWithoutSunlight();

	boolean isLighting();

	default RenderSettings copy() {
		return new StaticRenderSettings(
				isAmbientOcclusion(),
				isLighting(),
				isExcludeFacesWithoutSunlight()
			);
	}
	
	class StaticRenderSettings implements RenderSettings {
		
		private boolean ambientOcclusion;
		private boolean lighting;
		private boolean excludeFacesWithoutSunlight;
		
		private StaticRenderSettings(boolean ambientOcclusion, boolean lighting, boolean excludeFacesWithoutSunlight) {
			this.ambientOcclusion = ambientOcclusion;
			this.lighting = lighting;
			this.excludeFacesWithoutSunlight = excludeFacesWithoutSunlight;
		}

		public boolean isAmbientOcclusion() {
			return ambientOcclusion;
		}

		public boolean isLighting() {
			return lighting;
		}

		public boolean isExcludeFacesWithoutSunlight() {
			return excludeFacesWithoutSunlight;
		}
		
	}
	
}
