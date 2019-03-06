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
package de.bluecolored.bluemap.render.hires;

import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import com.flowpowered.math.vector.Vector4f;

import de.bluecolored.bluemap.api.Block;
import de.bluecolored.bluemap.api.ChunkNotGeneratedException;
import de.bluecolored.bluemap.api.WorldChunk;
import de.bluecolored.bluemap.logger.Logger;
import de.bluecolored.bluemap.render.RenderSettings;
import de.bluecolored.bluemap.render.WorldTile;
import de.bluecolored.bluemap.render.context.WorldChunkBlockContext;
import de.bluecolored.bluemap.render.hires.blockmodel.BlockStateModel;
import de.bluecolored.bluemap.render.hires.blockmodel.BlockStateModelFactory;
import de.bluecolored.bluemap.resourcepack.InvalidResourceDeclarationException;
import de.bluecolored.bluemap.resourcepack.NoSuchResourceException;
import de.bluecolored.bluemap.resourcepack.NoSuchTextureException;
import de.bluecolored.bluemap.resourcepack.ResourcePack;
import de.bluecolored.bluemap.util.AABB;
import de.bluecolored.bluemap.util.MathUtil;

public class HiresModelRenderer {

	private BlockStateModelFactory modelFactory;
	private Logger logger;
	
	public HiresModelRenderer(ResourcePack resourcePack) {
		this(resourcePack, Logger.stdOut());
	}
	
	public HiresModelRenderer(ResourcePack resourcePack, Logger logger) {
		this(new BlockStateModelFactory(resourcePack), logger);
	}
	
	public HiresModelRenderer(BlockStateModelFactory modelFactory) {
		this(modelFactory, Logger.stdOut());
	}
	
	public HiresModelRenderer(BlockStateModelFactory modelFactory, Logger logger) {
		this.modelFactory = modelFactory;
		this.logger = logger;
	}
	
	public HiresModel render(WorldTile tile, AABB region, RenderSettings renderSettings) throws ChunkNotGeneratedException {
		Vector3i min = region.getMin().toInt();
		Vector3i max = region.getMax().toInt();
		WorldChunk chunk = tile.getWorld().getWorldChunk(region.expand(4, 0, 4));
		
		HiresModel model = new HiresModel(tile.getWorld().getUUID(), tile.getTile(), min, max);
		
		for (int x = min.getX(); x <= max.getX(); x++){
			for (int z = min.getZ(); z <= max.getZ(); z++){

				int maxHeight = 0;
				Vector4f color = Vector4f.ZERO;
				
				for (int y = min.getY(); y <= max.getY(); y++){
					Block block = chunk.getBlock(x, y, z);
					if (block.getBlockState().getResourceId().equals("air")) continue;
					
					maxHeight = y;

					BlockStateModel blockModel;
					try {
						blockModel = modelFactory.createFrom(block.getBlockState(), new WorldChunkBlockContext(chunk, new Vector3i(x, y, z)), renderSettings);
					} catch (NoSuchResourceException | InvalidResourceDeclarationException | NoSuchTextureException e) {
						blockModel = new BlockStateModel();						
						logger.noFloodWarning("HiresModelRenderer-blockmodelerr-" + block.getBlockState().getResourceId(), "Failed to create BlockModel for BlockState: " + block.getBlockState() + " (" + e.toString() + ")");
					}
					
					blockModel.translate(new Vector3f(x, y, z).sub(min.toFloat()));

					color = MathUtil.overlayColors(blockModel.getMapColor(), color);
					
					//TODO: quick hack to random offset grass
					if (block.getBlockState().getResourceId().equals("tall_grass")){
						float dx = (MathUtil.hashToFloat(x, y, z, 123984) - 0.5f) * 0.75f;
						float dz = (MathUtil.hashToFloat(x, y, z, 345542) - 0.5f) * 0.75f;
						blockModel.translate(new Vector3f(dx, 0, dz));
					}
					
					model.merge(blockModel);
				}

				model.setHeight(x, z, maxHeight);
				model.setColor(x, z, color);
				
			}
		}
		
		return model;
	}
	
}
