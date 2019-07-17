/*
* Group 7
* P5
* 6/9/2019
* The File System program which provides system calls of the file system for the user that include format, open,
* read, write, seek, close, delete, fsize.
*/

public class FileSystem {
    private SuperBlock superblock;
    private Directory directory;
    private FileTable fileTable;
    private final static boolean SUCCESS = true;
    private final static boolean FAILURE = false;

    private static final int SEEK_SET = 0;
    private static final int SEEK_CUR = 1;
    private static final int SEEK_END = 2;

    /* FileSystem(int diskBlocks)
     * Initializes superblock, directory and file table based on the number of given disk blocks
     * Parameter diskBlocks is the number of disk blocks.
     */
    public FileSystem( int diskBlocks ) {
        superblock = new SuperBlock(diskBlocks);
        directory = new Directory(superblock.inodeBlocks);
        fileTable = new FileTable(directory);

        FileTableEntry dirEnt = open("/", "r");
        int dirSize = fsize(dirEnt);
        if (dirSize > 0) {
            byte[] dirData = new byte[dirSize];
            read(dirEnt, dirData);
            directory.bytes2directory(dirData);
        }
        close(dirEnt);
    }

    /* format(int files)
     * Formats the file system with the given number of files
     * Parameter files is the number of files to be created in the file system
     * Returns true if successfully format, otherwise return false
     */
    public boolean format(int files) {
        if (files > 0) {
            superblock.format(files);
            directory = new Directory(superblock.inodeBlocks);
            fileTable = new FileTable(directory);
            return SUCCESS;
        }
        return FAILURE;
    }

    /* close(FileTableEntry entry)
     * Closes the entry from the file table of the file system
     * Parameter entry is the file table entry to be closed
     * Return true if successfully closed, otherwise return false
     */
    public boolean close(FileTableEntry entry) {
        // if the entry is not valid to close
        if (entry == null) {
            return FAILURE;
        } else {
            // we want to synchronize so that other thread is not modifying/accessing
            synchronized (entry) {
                entry.count--;
                if (entry.count == 0) {
                    fileTable.ffree(entry);
                    return SUCCESS;
                }
                return FAILURE;
            }
        }
    }

    /* delete(String fileName)
     * Removes the file from the file system.
     * Parameter fileName is the name of the file to be deleted
     * Returns true if successfully deleted, otherwise return false
     */
    public boolean delete(String fileName) {
        FileTableEntry entry = open(fileName, "w");
        if (directory.ifree(entry.iNumber) && close(entry)) {
            return SUCCESS;
        } else {
            return FAILURE;
        }
    }

    /* fsize(FileTableEntry entry)
     * Gets the size of the file of the entry
     * Parameter entry is the File Table Entry that we retrive size
     * Return the length of the file
     */
    public int fsize(FileTableEntry entry) {
        if (entry == null) {
            return -1;
        }
        synchronized (entry) {
            return entry.inode.length;
        }
    }

    /* read(FileTableEntry entry, byte[] buffer)
     * Reads the file with the size of the buffer.
     * Paramter entry is the File Table Entry and buffer is the buffer that is used to read the file.
     * Returns the number of data read if successful, otherwise return 0.
     */
    public int read(FileTableEntry entry, byte[] buffer) {
        // we cannot read when someone is writing
        if ((entry.mode == "w") || (entry.mode == "a")) {
            return -1;
        }

        int size = buffer.length;
        int read = 0;
        int error = -1;
        int blockSize = 512;
        int left = 0;

        synchronized(entry) {
            while (entry.seekPtr < fsize(entry) && size > 0) {
                int currentBlock = entry.inode.findTargetBlock(entry.seekPtr);
                if (currentBlock == error) {
                    break;
                }
                byte[] data = new byte[blockSize];
                SysLib.rawread(currentBlock, data);

                int offset = entry.seekPtr % blockSize;
                int blocksLeft = blockSize - left;
                int fileLeft = fsize(entry) - entry.seekPtr;

                if (blocksLeft < fileLeft) {
                    left = blocksLeft;
                } else {
                    left = fileLeft;
                }

                if (left > size) {
                    left = size;
                }

                System.arraycopy(data, offset, buffer, read, left);
                read += left;
                entry.seekPtr += left;
                size -= left;
            }
            return read;
        }

    }

    /* seek(FileTableEntry entry, int offset, int whence)
     * Sets the seek pointer of the File Table Entry
     * Parameter entry is the File Table Entry, offset is the offset to be given,
     * whence is the location of the seek pointer to be set
     */
    public int seek(FileTableEntry entry, int offset, int whence) {
        synchronized (entry) {
            switch(whence) {
                case SEEK_SET:
                    entry.seekPtr = offset;
                    break;

                case SEEK_CUR:
                    entry.seekPtr += offset;
                    break;

                case SEEK_END:
                    entry.seekPtr = entry.inode.length + offset;
                    break;

                default:
                    return -1;
            }

            // if seek pointer is negative value, set to the start of the file
            if (entry.seekPtr < 0) {
                entry.seekPtr = 0;
            }

            // if seek pointer is greater than the length of the file, set to the file length
            if (entry.seekPtr > entry.inode.length) {
                entry.seekPtr = entry.inode.length;
            }
        }
        return entry.seekPtr;
    }

    /* deallocAllBlocks(FileTableEntry fileTableEntry)
     * Desallocate all blocks for the corresponding File Table Entry
     * Parameter FileTableEntry is the File Table Entry to disallocate blocks
     * Return true of successful, otherwise, return false
     */
    private boolean deallocAllBlocks(FileTableEntry fileTableEntry) {
        if (fileTableEntry.inode.count != 1)
        {
            return FAILURE;
        }

        // Deallocate blocks pointed by direct pointers
        for (short blockId = 0; blockId < 11; blockId++)
        {
            if (fileTableEntry.inode.direct[blockId] != -1)
            {
                superblock.returnBlock(blockId);
                fileTableEntry.inode.direct[blockId] = -1;
            }
        }


        byte [] data = fileTableEntry.inode.unregisterIndexBlock();

        // Deallocate blocks pointed by indirect pointer
        if (data != null)
        {
            short blockId;
            while((blockId = SysLib.bytes2short(data, 0)) != -1)
            {
                superblock.returnBlock(blockId);
            }
        }
        fileTableEntry.inode.toDisk(fileTableEntry.iNumber);
        return SUCCESS;
    }

    /* open(String filename, String mode)
     * Opens the file with the given mode.
     * Parameter filename is the name of the file, and mode is the mode of accessing the file.
     * Returns File Table Entry for the corresponding file name, otherwise return null.
     */
    public FileTableEntry open(String filename, String mode){
        FileTableEntry ftEntry = fileTable.falloc(filename, mode);
        // check if writing mode
        if (mode == "w")
        {
            // if so, make sure all blocks are unallocated
            if ( !deallocAllBlocks( ftEntry ))
            {
                return null;
            }
        }
        return ftEntry;
    }

    /* write(FileTableEntry entry, byte[] buffer)
     * Writes the contents of buffer to the file.
     * Parameter entry is the File Table Entry of the file, buffer is the buffer which contains data.
     * Returns the number of bytes written, otherwise return 0.
     */
    public int write(FileTableEntry entry, byte[] buffer){
        int bytesWritten = 0;
        int bufferSize = buffer.length;
        int blockSize = 512;

        if (entry == null || entry.mode == "r")
        {
            return -1;
        }

        synchronized (entry)
        {
            while (bufferSize > 0)
            {
                int location = entry.inode.findTargetBlock(entry.seekPtr);

                // if current block null
                if (location == -1)
                {
                    short newLocation = (short) superblock.getFreeBlock();

                    int testPtr = entry.inode.registerTargetBlock(entry.seekPtr, newLocation);

                    if (testPtr == -3)
                    {
                        short freeBlock = (short) this.superblock.getFreeBlock();

                        // indirect pointer is empty
                        if (!entry.inode.registerIndexBlock(freeBlock))
                        {
                            return -1;
                        }

                        // check block pointer error
                        if (entry.inode.registerTargetBlock(entry.seekPtr, newLocation) != 0)
                        {
                            return -1;
                        }

                    }
                    else if (testPtr == -2 || testPtr == -1)
                    {
                        return -1;
                    }

                    location = newLocation;
                }

                byte [] tempBuff = new byte[blockSize];
                SysLib.rawread(location, tempBuff);

                int tempPtr = entry.seekPtr % blockSize;
                int diff = blockSize - tempPtr;

                if (diff > bufferSize)
                {
                    System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, bufferSize);
                    SysLib.rawwrite(location, tempBuff);

                    entry.seekPtr += bufferSize;
                    bytesWritten += bufferSize;
                    bufferSize = 0;
                }
                else {
                    System.arraycopy(buffer, bytesWritten, tempBuff, tempPtr, diff);
                    SysLib.rawwrite(location, tempBuff);

                    entry.seekPtr += diff;
                    bytesWritten += diff;
                    bufferSize -= diff;
                }
            }

            if (entry.seekPtr > entry.inode.length)
            {
                entry.inode.length = entry.seekPtr;
            }
            entry.inode.toDisk(entry.iNumber);
            return bytesWritten;
        }
    }

    /* sync()
     * Synchronize and write data back to the disk
     */
    public void sync()
    {
        FileTableEntry entry = open("/", "w");
        byte[] temp = directory.directory2bytes();

        // Write back to the disk all the info from the directory
        write(entry, temp);
        close(entry);
        superblock.sync();

    }
}