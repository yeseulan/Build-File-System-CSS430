/*
 * Group 7
 * P5
 * 6/9/2019
 * A class Superblock that contains overall information about the file system
 * including total blocks, inodes, and free list.
 */

public class SuperBlock {

    private final int defaultInodeBlocks = 64;
    public int totalBlocks;
    public int inodeBlocks;
    public int freeList;

    /*
     * SuperBlock(int diskSize)
     * Constructor that initializes values that are stored in the disk block 0
     * Parameter diskSize is the size of the disk.
     */
    public SuperBlock(int diskSize) {
        byte[] superBlock = new byte[Disk.blockSize]; //blockSize = 512;
        SysLib.rawread(0, superBlock); // Read block 0 from disk to the superBlock
        totalBlocks = SysLib.bytes2int(superBlock, 0);
        inodeBlocks = SysLib.bytes2int(superBlock, 4);
        freeList = SysLib.bytes2int(superBlock, 8);

        if (totalBlocks == diskSize && inodeBlocks > 0 && freeList >= 2) {
            return ;
        } else {
            totalBlocks = diskSize;
            format(defaultInodeBlocks);
        }
    }

    /*
     * sync()
     * Write back block information including
     * totalBlocks, inodeBlocks, and freeList to disk
     */
    public void sync() {
        byte[] blockInfo = new byte[Disk.blockSize];
        SysLib.int2bytes(totalBlocks, blockInfo, 0);
        SysLib.int2bytes(inodeBlocks, blockInfo, 4);
        SysLib.int2bytes(freeList, blockInfo, 8);
        SysLib.rawwrite(0, blockInfo);
    }

    /*
     * getFreeBlock()
     * Dequeue and returns the first free block from the free list.
     * If there is abscence of free block, -1 is returned
     */
    public int getFreeBlock() {
        int freeBlock = freeList;

        if (freeList > 0) {
            if (freeList < totalBlocks) {
                byte[] blockInfo = new byte[Disk.blockSize];
                SysLib.rawread(freeList, blockInfo);

                // Update the free list info
                freeList = SysLib.bytes2int(blockInfo, 0);

                SysLib.int2bytes(0, blockInfo, 0);
                SysLib.rawwrite(freeBlock, blockInfo);
                return freeBlock;
            }
        }
        return freeBlock;
    }

    /*
     * returnBlock(blockNumber)
     * Enqueue a given block to the front of the free list
     * Parameter blockNumber is the block number that we
     * want to make free
     */
    public boolean returnBlock(int blockNumber) {
        if (blockNumber < 0) {
            return false;
        } else {
            byte[] tmpBlock = new byte[Disk.blockSize];
            SysLib.int2bytes(freeList, tmpBlock, 0);
            SysLib.rawwrite(blockNumber, tmpBlock);
            freeList = blockNumber;
            return true;
        }
    }

    /*
    * format(int numberOfBlocks)
    * Creates inodes based on the given number of blocks
    * Parameter numberOfBlocks are the number of blocks
    * Links free block list to each other
    */
    public void format(int numberOfBlocks) {
        inodeBlocks = numberOfBlocks;
        for (short i = 0; i < inodeBlocks; i++) {
            Inode tmpNode = new Inode();
            tmpNode.flag = 0;
            tmpNode.toDisk(i);
        }

        freeList = 2 + (inodeBlocks / 16);

        // Link the next available block from the current free block
        for (int i = freeList; i < this.totalBlocks; i++) {
            byte[] tmpBlock = new byte[Disk.blockSize];
            SysLib.int2bytes(i + 1, tmpBlock, 0);
            SysLib.rawwrite(i, tmpBlock);
        }
        this.sync();
    }
}