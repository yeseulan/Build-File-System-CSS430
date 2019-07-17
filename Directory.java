/*
* Group 7
* P5
* 6/9/2019
* Directory program which is utilized in File System that 
* maintains directory entries of files and thier corresponding inode number
*/

public class Directory {
    private static int maxChars = 30; // max characters of each file name
    
    // Directory entries
    private int fsizes[];    // each element stores a different file size
    private char fnames[][];    // each element stores a different file name

    public Directory(int maxInumber) {
        fsizes = new int[maxInumber];
        for (int i = 0; i < maxInumber; i++) {
            fsizes[i] = 0;
        }
        fnames = new char[maxInumber][maxChars];
        String root ="/";
        fsizes[0] = root.length();
        root.getChars(0, fsizes[0], fnames[0], 0);
    }

    /*
    * bytes2directory(byte data[])
    * Parameter data[] is the directy information received from the disk
    * Initilaizes directory instance with the given byte array of data
    */
    public void bytes2directory(byte data[]) {
        int offset = 0;
        for (int i = 0; i < fsizes.length; i++) {
            fsizes[i] = SysLib.bytes2int(data, offset);
            offset += 4;
        }

        for (int j = 0; j < fsizes.length; j++) {
            String name = new String(data, offset, maxChars * 2);
            name.getChars(0, fsizes[j], fnames[j], 0);
            offset += maxChars * 2;
        }
    }

    /*
    * directory2bytes()
    * Converts directory information into a byte array
    * Return converted byte array 
    */
    public byte[] directory2bytes() {
        byte[] dirEntry = new byte[fsizes.length * 4 + fnames.length * maxChars * 2];
        int offset = 0;
        
        // Covert data in file size to to bytes
        for (int i = 0; i < fsizes.length; i++) {
            SysLib.int2bytes(fsizes[i], dirEntry, offset);
            offset += 4;
        }

        // Convert data in fileName to bytes
        for (int i = 0; i < fnames.length; i++) {
            String name = new String(fnames[i], 0, fsizes[i]);
            byte[] data = name.getBytes();
            System.arraycopy(data, 0, dirEntry, offset, data.length);
            offset += maxChars * 2;
        }
        return dirEntry;
    }

    /*
    * ialloc(String fileName)
    * Parameter fileName is the file to be created in the directory
    * Allocates a new inode number for given fileName and returns inode number
    * Returns -1 if it cannot allocate inode number
    */
    public short ialloc(String fileName) {
        for (int i = 0; i < fsizes.length; i++) {
            if (fsizes[i] == 0) {
                int length = fileName.length() > maxChars ? maxChars : fileName.length();
                fsizes[i] = length;
                fileName.getChars(0, fsizes[i], fnames[i], 0);
                return (short) i;
            }
        }
        return -1;
    }

    /*
    * ifree(short iNumber)
    * Parameter iNumber is the inode number to be disallocated
    * Disallocates the given inumber and delete the file from the direcotory
    * Returns true for successful disallocation, false otherwise
    */
    public boolean ifree(short iNumber) {
        if (iNumber < 0 || fsizes[iNumber] <= 0) {
            return false;
        }
        for (int i = 0; i < maxChars; i++) {
            fnames[iNumber][i] = 0;
        }
        fsizes[iNumber] = 0;
        return true;
    }

    /*
    * namei(String fileName)
    * Parameter fileName is the name of the file 
    * which looks for the iNumber
    * Finds the iNumber of given fileName and returns it
    * Returns -1 if it couldn't find the iNumber of the given file
    */
    public short namei(String fileName) {
        for (short i = 0; i < fsizes.length; i++) {
            if (fsizes[i] == fileName.length()) {
                String name = new String(fnames[i], 0, fsizes[i]);
                if (fileName.compareTo(name)== 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}