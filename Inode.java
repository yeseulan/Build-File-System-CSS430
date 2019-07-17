/*
* Group 7
* P5
* 6/9/2019
* Inode class which describes one file in the file system. The information to describe
* a file is stored such as length, count, flag, direct and indirect pointers.
*/

public class Inode {
    private final static int iNodeSize = 32;  // fix to 32 bytes
    private final static int directSize = 11; // # of direct pointers

    public int length;  // file size in bytes
    public short count; // # of file-table entries points to this inode
    public short flag; // 0 = unused, 1 = used, ...
    public short direct[] = new short[directSize]; // direct pointers array
    public short indirect; // a indirect pointer

    /*
    * Inode()
    * Constructor that initiliazes an inode.
    * Sets the variables that describes an inode
    * such as length, count, flag and direct and indirect pointers.
    */
    public Inode() {
        length = 0;
        count = 0;
        flag = 1;
        for (int i = 0; i < direct.length; i++) {
            direct[i] = -1;
        }
        indirect = -1;
    }

    /*
    * Inode(short iNumber)
    * Constructor which retrieves the existing inode from disk into the memory
    * Parameter iNumber is given iNode number
    * Initializes a new inode with the information of the inode that is retrieved
    */
    public Inode(short iNumber) {
        int blockNumber = 1 + iNumber / 16;
        byte[] data = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, data);

        int offset = iNumber % 16 * 32;
        length = SysLib.bytes2int(data, offset);
        offset += 4;
        count = SysLib.bytes2short(data, offset);
        offset += 2;
        flag = SysLib.bytes2short(data, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++) {
            short directBlock = SysLib.bytes2short(data, offset);
            direct[i] = directBlock;
            offset += 2;
        }
        indirect = SysLib.bytes2short(data, offset);
    }

    /*
    * toDisk(short iNumber)
    * Saves the new inode information to current inode in the disk
    * Parameter iNumber is inode number that is used to find the block number
    * in the disk
    */
    public void toDisk(short iNumber) {
        if (iNumber < 0) {
            return;
        }
        byte[] inodeInfo = new byte[iNodeSize];
        int offset = 0;

        // Saves variables converted to bytes into inodeInfo
        SysLib.int2bytes(length, inodeInfo, offset);
        offset += 4;
        SysLib.short2bytes(count, inodeInfo, offset);
        offset += 2;
        SysLib.short2bytes(flag, inodeInfo, offset);
        offset += 2;

        for (int i = 0; i < directSize; i++) {
            SysLib.short2bytes(direct[i], inodeInfo, offset);
            offset += 2;
        }

        SysLib.short2bytes(indirect, inodeInfo, offset);
        offset += 2;

        // Reads in the existing block from the disk and write back the
        // new inode info
        int blockNumber = 1 + iNumber / 16;
        byte[] newData = new byte[Disk.blockSize];
        SysLib.rawread(blockNumber, newData);
        offset = iNumber % 16 * iNodeSize;
        System.arraycopy(inodeInfo, 0, newData, offset, iNodeSize);
        SysLib.rawwrite(blockNumber, newData);
    }

    /*
    * getIndexBlock()
    * Find index bock number by returning the value of indirect pointer
    */
    public int findIndexBlock() {
        return indirect;
    }

    /*
     * registerIndexBlock(short blockNumber)
     * Register the index block and write back to the disk
     * Parameter blockNumber is the block number to be registered
     * Returns true if correctly registered, otherwise returns false
     */
    public boolean registerIndexBlock(short blockNumber) {
        for (int i = 0; i < directSize; i++) {
            if (direct[i] == -1) {
                return false;
            }
        }

        if (indirect != -1) {
            return false;
        }

        indirect = blockNumber;
        byte[] data = new byte[Disk.blockSize];

        for (int i = 0; i < (Disk.blockSize / 2); i++) {
            SysLib.short2bytes((short)-1, data, i * 2);
        }
        SysLib.rawwrite(blockNumber, data);
        return true;
    }

    /*
    * findTargetBlock(int offset)
    * Finds the target blcok in the disk with the given offset
    * Parameter offset is the offset used to find the block
    * Returns target block number if found, otherwise return -1
    */
    public int findTargetBlock(int offset) {
        int targetBlock = offset / Disk.blockSize;

        // If target block is found in the direct pointers
        if (targetBlock < directSize) {
            return direct[targetBlock];
        } else if (indirect == -1) {
            return -1;
        } else {
            // if target block is found in the indirect pointer
            byte[] tmpData = new byte[Disk.blockSize];
            SysLib.rawread(indirect, tmpData);

            int blockSpace = (targetBlock - directSize) * 2;
            return SysLib.bytes2short(tmpData, blockSpace);
        }
    }

    /*
     * registerTargetBlock(int offset, short block)
     * Retisters the block at the given offset and block number.
     * Parameters are offset, block value that are used to find target
     * Returns integer values to indicate different status
     */
    public int registerTargetBlock(int offset, short block) {
        int target = offset / Disk.blockSize;

        if (target < directSize) {
            if (direct[target] >= 0) { // this position has the block already
                return -1;
            } else if (target > 0 && direct[target-1] == -1) {
                return -2;
            } else {  // set the block number to the target position in direct pointer
                direct[target] = block;
                return 0;
            }
        } else if (indirect < 0) { // if indirect is not available
            return -3;
        } else {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);
            int diff = target - 11;
            if (SysLib.bytes2short(data, diff * 2) > 0) {
                return -1;
            } else {
                SysLib.short2bytes(block, data, diff * 2);
                SysLib.rawwrite(indirect, data);
                return 0;
            }
        }
    }

    /*
    * unregisterIndexBlock()
    * Unregisters the indirect index block by resetting the
    * indirect pointer
    * Returns unregistered block if successful, otherwise return null
     */
    public byte[] unregisterIndexBlock() {
        if (indirect >= 0) {
            byte[] data = new byte[Disk.blockSize];
            SysLib.rawread(indirect, data);
            indirect = -1;
            return data;
        } else {
            return null;
        }
    }
}