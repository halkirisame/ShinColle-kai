package com.lulan.shincolle.ai.domain.command;

import com.lulan.shincolle.ai.domain.DimensionKey;
import java.util.UUID;

public record LegacyCommandFields(int guardX, int guardY, int guardZ, int guardDim, int guardType,
                                  DimensionKey guardedDimension, UUID guardedEntityUuid,
                                  boolean canFollow, boolean releaseOnArrival, boolean orderedToSit,
                                  int guardId) { }
