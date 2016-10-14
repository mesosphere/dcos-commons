package org.apache.mesos.scheduler.plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the default strategy for executing a phase where there is a pause after the first block.
 */
public class DefaultStageStrategy implements PhaseStrategy {
  private static final Logger logger = LoggerFactory.getLogger(DefaultStageStrategy.class);

  protected final Phase phase;

  private boolean[] shouldStart;
  private int currPos = 0;

  public DefaultStageStrategy(Phase phase) {
    this.phase = phase;
    this.shouldStart = initializeDecisionMap(phase.getBlocks());
    advancePosition();
  }

  private static boolean[] initializeDecisionMap(List<? extends Block> blocks) {
    int blockCount = blocks.size();
    boolean[] shouldStart = new boolean[blockCount];

    int incompleteCount = 0;
    for (int i = 0; i < blockCount; i++) {
      Block block = blocks.get(i);

      if (!block.isComplete()) {
        incompleteCount++;
      }

      // Boolean array defaults to false.  The first 2 blocks will need
      // explicit operations from a user to initiate.  The first block
      // of a Phase must always be manually initiated.  We should also wait
      // after the first block is complete to determine if we should proceed.
      if (incompleteCount > 2 || block.isComplete()) {
        shouldStart[i] = true;
      }
    }
    return shouldStart;
  }

  private void advancePosition() {
    int blockCount = phase.getBlocks().size();
    synchronized (this) {
      for (int i = 0; i < blockCount; i++) {
        Block currBlock = phase.getBlocks().get(i);
        if (!currBlock.isComplete()) {
          currPos = i;
          return;
        } else {
          shouldStart[i] = true;
        }
      }
    }
  }

  @Override
  public Optional<Block> getCurrentBlock() {
    //TODO(nick) the returned Block is not guaranteed to stay in a consistent state.
    //alternative: instead provide a thread-safe wrapper for viewing/changing current block state
    synchronized (this) {
      advancePosition();
      if (shouldStart[currPos]) {
        Block block = phase.getBlocks().get(currPos);
        logger.info(String.format("Returning block '%s' at position: %d", block.getName(), currPos));
        return Optional.of(block);
      } else {
        // WAITING for input, don't continue to next block
        return Optional.empty();
      }
    }
  }

  @Override
  public void proceed() {
    synchronized (this) {
      advancePosition();
      shouldStart[currPos] = true;
    }
  }

  @Override
  public void interrupt() {
    int blockCount = phase.getBlocks().size();
    synchronized (this) {
      advancePosition();
      if (shouldStart[currPos] && currPos + 1 < blockCount) {
        shouldStart[currPos + 1] = false;
      }
    }
  }

  @Override
  public void restart(UUID blockId) {
    for (Block block : phase.getBlocks()) {
      if (block.getId().equals(blockId)) {
        block.restart();
      }
    }

    advancePosition();
  }

  @Override
  public void forceComplete(UUID blockId) {
    logger.warn("Force complete not implemented.");
  }

  @Override
  public Phase getPhase() {
    //TODO(nick) the Blocks in the returned Phase are not guaranteed to stay in a consistent state.
    //alternative: instead provide a thread-safe wrapper for viewing block state
    return phase;
  }

  @Override
  public boolean isInterrupted() {
    return getStatus() == Status.WAITING;
  }

  @Override
  public Status getStatus() {
    int blockIndex = -1;
    for (Block block : phase.getBlocks()) {
      blockIndex++;

      if (!block.isComplete()) {
        if (block.isPending() && hasDecisionPoint(block)) {
          return Status.WAITING;
        }

        if (blockIndex > 0) {
          return Status.IN_PROGRESS;
        } else {
          return Block.getStatus(block);
        }
      }
    }

    return Status.COMPLETE;
  }

  @Override
  public boolean hasDecisionPoint(Block block) {
    int blockIndex = -1;
    for (Block blk : phase.getBlocks()) {
      blockIndex++;

      if (blk.getId().equals(block.getId())) {
        synchronized (this) {
          return !shouldStart[blockIndex];
        }
      }
    }

    return false;
  }
}
