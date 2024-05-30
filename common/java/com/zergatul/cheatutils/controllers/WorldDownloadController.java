package com.zergatul.cheatutils.controllers;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.zergatul.cheatutils.chunkoverlays.WorldDownloadChunkOverlay;
import com.zergatul.cheatutils.mixins.common.accessors.ChunkSerializerAccessor;
import com.zergatul.cheatutils.utils.Dimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WorldDownloadController {

    public static final WorldDownloadController instance = new WorldDownloadController();

    private final Minecraft mc = Minecraft.getInstance();
    private final Logger logger = LogManager.getLogger(WorldDownloadController.class);
    private Map<ResourceKey<Level>, ChunkStorage> chunkStorages;
    private LevelStorageSource.LevelStorageAccess access;

    public WorldDownloadController() {}

    public boolean isActive() {
        return chunkStorages != null;
    }

    public void start(String name) {
        final Object syncObject = new Object();
        RenderSystem.recordRenderCall(() -> {
            try {
                stopInternal();

                File file = new File("./saves/" + name + "/level.dat");
                if (!file.exists()) {
                    throw new IllegalStateException("World [" + name + "] doesn't exist in [saves] directory.");
                }

                access = mc.getLevelSource().createAccess(name);
                chunkStorages = new HashMap<>();
                ChunkOverlayController.instance.ofType(WorldDownloadChunkOverlay.class).onEnabledChanged();
            } catch (Throwable e) {
                logger.error("Cannot start World Download", e);
                stopInternal();
            } finally {
                synchronized (syncObject) {
                    syncObject.notify();
                }
            }
        });

        try {
            synchronized (syncObject) {
                syncObject.wait();
            }
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    public void stop() {
        final Object syncObject = new Object();
        RenderSystem.recordRenderCall(() -> {
            try {
                stopInternal();
            } finally {
                synchronized (syncObject) {
                    syncObject.notify();
                }
            }
        });

        try {
            synchronized (syncObject) {
                syncObject.wait();
            }
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    public void onChunkFilledFromPacket(LevelChunk chunk) {
        if (isActive()) {
            processChunk(chunk);
        }
    }

    private void stopInternal() {
        try {
            if (chunkStorages != null) {
                for (ChunkStorage storage : chunkStorages.values()) {
                    try {
                        storage.flushWorker();
                        storage.close();
                    } catch (IOException e) {
                        logger.error("Cannot save ChunkStorage", e);
                    }
                }
            }

            if (mc.player != null && access != null) {
                PlayerDataStorage playerDataStorage = access.createPlayerStorage();
                playerDataStorage.save(mc.player);
            }

            chunkStorages = null;

            closeAccess();

            ChunkOverlayController.instance.ofType(WorldDownloadChunkOverlay.class).onEnabledChanged();
        } catch (Throwable e) {
            logger.error("Cannot stop World Download", e);
            stop();
        }
    }

    private void processChunk(LevelChunk chunk) {
        try {
            ClientLevel level = (ClientLevel) chunk.getLevel();
            Dimension dimension = Dimension.get(level);
            ResourceKey<Level> levelDimension = level.dimension();
            ChunkStorage storage;
            if (chunkStorages.containsKey(levelDimension)) {
                storage = chunkStorages.get(levelDimension);
            } else {
                storage = new ChunkStorage(
                        access.getDimensionPath(levelDimension).resolve("region"),
                        null, // data fixer
                        true); // sync
                chunkStorages.put(levelDimension, storage);
            }

            CompoundTag compoundtag = write(level, chunk);
            storage.write(chunk.getPos(), compoundtag);

            ChunkOverlayController.instance.ofType(WorldDownloadChunkOverlay.class).notifyChunkSaved(
                    dimension, chunk.getPos().x, chunk.getPos().z);
        } catch (Throwable e) {
            logger.error("Cannot save chunk", e);
        }
    }

    // copied from ChunkSerializer.write
    private CompoundTag write(ClientLevel level, ChunkAccess chunk) {
        ChunkPos chunkpos = chunk.getPos();
        CompoundTag compoundtag = NbtUtils.addCurrentDataVersion(new CompoundTag());
        compoundtag.putInt("xPos", chunkpos.x);
        compoundtag.putInt("yPos", chunk.getMinSection());
        compoundtag.putInt("zPos", chunkpos.z);
        compoundtag.putLong("LastUpdate", level.getGameTime());
        compoundtag.putLong("InhabitedTime", chunk.getInhabitedTime());
        compoundtag.putString("Status", BuiltInRegistries.CHUNK_STATUS.getKey(chunk.getStatus()).toString());
        BlendingData blendingdata = chunk.getBlendingData();
        if (blendingdata != null) {
            BlendingData.CODEC.encodeStart(NbtOps.INSTANCE, blendingdata).resultOrPartial(logger::error).ifPresent((p_196909_) -> {
                compoundtag.put("blending_data", p_196909_);
            });
        }

        BelowZeroRetrogen belowzeroretrogen = chunk.getBelowZeroRetrogen();
        if (belowzeroretrogen != null) {
            BelowZeroRetrogen.CODEC.encodeStart(NbtOps.INSTANCE, belowzeroretrogen).resultOrPartial(logger::error).ifPresent((p_188279_) -> {
                compoundtag.put("below_zero_retrogen", p_188279_);
            });
        }

        UpgradeData upgradedata = chunk.getUpgradeData();
        if (!upgradedata.isEmpty()) {
            compoundtag.put("UpgradeData", upgradedata.write());
        }

        LevelChunkSection[] alevelchunksection = chunk.getSections();
        ListTag listtag = new ListTag();
        LevelLightEngine levellightengine = level.getChunkSource().getLightEngine();
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Codec<PalettedContainerRO<Holder<Biome>>> codec = makeBiomeCodec(registry);
        boolean flag = chunk.isLightCorrect();

        for(int i = levellightengine.getMinLightSection(); i < levellightengine.getMaxLightSection(); ++i) {
            int j = chunk.getSectionIndexFromSectionY(i);
            boolean flag1 = j >= 0 && j < alevelchunksection.length;
            DataLayer datalayer = levellightengine.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(chunkpos, i));
            DataLayer datalayer1 = levellightengine.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(chunkpos, i));
            if (flag1 || datalayer != null || datalayer1 != null) {
                CompoundTag compoundtag1 = new CompoundTag();
                if (flag1) {
                    LevelChunkSection levelchunksection = alevelchunksection[j];
                    compoundtag1.put("block_states", ChunkSerializerAccessor.getBlockStateCodec_CU().encodeStart(NbtOps.INSTANCE, levelchunksection.getStates()).getOrThrow(false, logger::error));
                    compoundtag1.put("biomes", codec.encodeStart(NbtOps.INSTANCE, levelchunksection.getBiomes()).getOrThrow(false, logger::error));
                }

                if (datalayer != null && !datalayer.isEmpty()) {
                    compoundtag1.putByteArray("BlockLight", datalayer.getData());
                }

                if (datalayer1 != null && !datalayer1.isEmpty()) {
                    compoundtag1.putByteArray("SkyLight", datalayer1.getData());
                }

                if (!compoundtag1.isEmpty()) {
                    compoundtag1.putByte("Y", (byte)i);
                    listtag.add(compoundtag1);
                }
            }
        }

        compoundtag.put("sections", listtag);
        if (flag) {
            compoundtag.putBoolean("isLightOn", true);
        }

        ListTag listtag1 = new ListTag();

        for(BlockPos blockpos : chunk.getBlockEntitiesPos()) {
            CompoundTag compoundtag3 = chunk.getBlockEntityNbtForSaving(blockpos);
            if (compoundtag3 != null) {
                listtag1.add(compoundtag3);
            }
        }

        compoundtag.put("block_entities", listtag1);
        /*if (chunk.getStatus().getChunkType() == ChunkStatus.ChunkType.PROTOCHUNK) {
            ProtoChunk protochunk = (ProtoChunk)chunk;
            ListTag listtag2 = new ListTag();
            listtag2.addAll(protochunk.getEntities());
            compoundtag.put("entities", listtag2);
            CompoundTag compoundtag4 = new CompoundTag();

            for(GenerationStep.Carving generationstep$carving : GenerationStep.Carving.values()) {
                CarvingMask carvingmask = protochunk.getCarvingMask(generationstep$carving);
                if (carvingmask != null) {
                    compoundtag4.putLongArray(generationstep$carving.toString(), carvingmask.toArray());
                }
            }

            compoundtag.put("CarvingMasks", compoundtag4);
        }*/
        /*else if (chunk instanceof LevelChunk levelChunk){
            try {
                final CompoundTag capTag = levelChunk.writeCapsToNBT();
                if (capTag != null) compoundtag.put("ForgeCaps", capTag);
            } catch (Exception exception) {
                logger.error("A capability provider has thrown an exception trying to write state. It will not persist. Report this to the mod author", exception);
            }
        }*/

        saveTicks(level, compoundtag, chunk.getTicksForSerialization());
        compoundtag.put("PostProcessing", ChunkSerializer.packOffsets(chunk.getPostProcessing()));
        CompoundTag compoundtag2 = new CompoundTag();

        for(Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
            if (chunk.getStatus().heightmapsAfter().contains(entry.getKey())) {
                compoundtag2.put(entry.getKey().getSerializationKey(), new LongArrayTag(entry.getValue().getRawData()));
            }
        }

        compoundtag.put("Heightmaps", compoundtag2);
        //compoundtag.put("structures", packStructureData(StructurePieceSerializationContext.fromLevel(level), chunkpos, chunk.getAllStarts(), chunk.getAllReferences()));
        return compoundtag;
    }

    private Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> p_188261_) {
        return PalettedContainer.codecRO(p_188261_.asHolderIdMap(), p_188261_.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, p_188261_.getHolderOrThrow(Biomes.PLAINS));
    }

    private void closeAccess() {
        if (access != null) {
            try {
                access.close();
            }
            catch (Throwable e) {
                logger.error("Cannot close LevelStorageAccess", e);
            }
            access = null;
        }
    }

    private static void saveTicks(ClientLevel p_188236_, CompoundTag p_188237_, ChunkAccess.TicksToSave p_188238_) {
        long i = p_188236_.getLevelData().getGameTime();
        p_188237_.put("block_ticks", p_188238_.blocks().save(i, (p_258987_) -> {
            return BuiltInRegistries.BLOCK.getKey(p_258987_).toString();
        }));
        p_188237_.put("fluid_ticks", p_188238_.fluids().save(i, (p_258989_) -> {
            return BuiltInRegistries.FLUID.getKey(p_258989_).toString();
        }));
    }
}