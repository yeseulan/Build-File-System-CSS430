/*
* Group 7
* P5
* 6/9/2019
* File Table class is represents the file table entries. When it received the  
* required for read or write, it can create a file table entry. Then, delete 
* the data from the file table entry.
*/

import java.util.Vector;

public class FileTable {
	private Vector table;//set the entiry of the file table
	private Directory dir;//set root directory
	public final static int UNUSED = 0;
	public final static int USED = 1;
	public final static int READ = 2;
	public final static int WRITE = 3;
	
	/*
	 * FileTable()
	 * constructor for set the file table
	 */
	public FileTable(Directory directory) {
		table = new Vector();//set struture table 
		dir = directory;//set director for receive a reference
	}
	
	/*
	 * FileTableEntry falloc()
	 * set file table entry for add data inside.
	 * use filename to access the file, if the file is null or no file
	 * it will create a file and read or write it. Then, count the user
	 * numbers and save in disk
	 */
	public synchronized FileTableEntry falloc(String filename, String mode) {
		short iNumber = -1; //inode number
		Inode inode = null;//set null for inode
		
		while (true) {
			//get iNumber from file name
			iNumber = (filename.equals("/")) ? (short) 0 : dir.namei(filename);
			
			//if the iNumber is greater or equal 0, exist the inode
			if (iNumber >= 0) {
				inode = new Inode(iNumber);
				
				//if the file request for read, and flag is read or used or unused
				if (mode.equals("r")) {
					if (inode.flag == READ || inode.flag == USED || inode.flag == UNUSED) {
						inode.flag = READ;//set the flag for read
						break;
						//if the file can write, wait until finish
					} else if (inode.flag == WRITE) {
						try {
							wait();
						} catch (InterruptedException e) {}
					}
				}  else {
					// I want to write, but no one is reading it
					if (inode.flag == USED || inode.flag == UNUSED) {
						inode.flag = WRITE;//set the flag for write
						break;
					} else { // I want to write, but someone is reading it
						try {
							wait();//if flag can read or write, wait until they finish
						} catch (InterruptedException e) {}
					}
				}
				//if the node can exist, return null
			} else if (mode.equals("r")) {
				return null;
			} else {
				//if the node cannot exist, create a new node and use directory to get iNumber
				if (!mode.equals("r")) {
					iNumber = dir.ialloc(filename);
					inode = new Inode(iNumber);
					inode.flag = WRITE;
					break;
				}
			}
		}
		
		inode.count++;//increstment for count the users number
		inode.toDisk(iNumber);
		//create a new file table entry 
		FileTableEntry entry = new FileTableEntry(inode, iNumber, mode);
		table.addElement(entry);//add data in the file table
		return entry;
	} 
	
	/*
	 * ffree(FileTableEntry entry)
	 * remove the data from file table entry. use boolean to check the data
	 * is in file table entry or not. If it is true, it need to remove it. if 
	 * it is false, it set invalid and cannnot find.
	 */
	public synchronized boolean ffree(FileTableEntry entry) {
		Inode inode = new Inode(entry.iNumber);//set the reference from file table
		
		 if (table.remove(entry))
	        {
			 //check the table for read data
	            if (inode.flag == READ)
	            {
	                if (inode.count == 1)
	                {
	                    // remove the data
	                    notify();
	                    inode.flag = USED;
	                }
	            }
	            //check the table for write data
	            else if (inode.flag == WRITE)
	            {
	                inode.flag = USED;
	                notifyAll();
	            }
	            //decreasement for count users number
	            inode.count--;
	            inode.toDisk(entry.iNumber);
	            return true;
	        }
	        return false;
	    }
	
	/*
	 * fempty()
	 * check the file table is empty or not
	 */
	public synchronized boolean fempty() {
		return table.isEmpty();//if the file table is empty, return to call the starting format
	}
}