## FileIndexerAndBackup

#### Index and manage files, create backups like Time Machine, sync files, find duplicate files and much more

The goal of this program is to index files (incl. hash and metadata) on different drives and to store this information within one or different database files.

Now, this information can be used to create backups like Time Machine on macOS but fully customizable and with much more user control.
For this kind of backups, a backup medium with hardlink support is needed (like EXT, NTFS, HFS+). The main advantage of this backup type is,
that only changed or new files are copied to the backup medium and all other files will be saved as hardlinks to already existing files on the backup medium. 
Thus, it is a combination of incremental and full backup.

For every backup a new folder with the current date (e.g. 20201124-101547) is created and within it all files and folders exactly like the source folder.
Another advantage is the file deduplication feature which is possible by the use of hardlinks.
Because all file information is stored in a database, you have information about what file (-version) is on which backup medium und you can also do a verification
(test if a bit rot happened or files got lost).

Other features are:
- index files within archives (7z, zip, tar, tar.gz, jar)
- synchronize files between two folders (one-way sync)
- compare two index runs / folders
- read EXIF information of images (incl. raw images), for example to check if the file modification date is the same as the date taken and to 
correct it
- implement your own tasks or backup strategy with ease
- mediums are recognized and indexed by their volume serial number

Future plans:
- customizable two-way sync between two folders
- Remove backup run (database content and files on backup medium; now, file deletion is a manual step after removing an index run)
- analyze, how much diskspace is used by a concrete backup run (because of the hardlink feature, all files of all other backup runs have to be considered)

Technical information:
- 100% written in Kotlin
- small code size: less than 2k lines of code!
- integrated SQLite database
- integrated own tiny entity to relational database mapper (only 250 lines of code)
- use of own small program argument parser (separate project)
- file indexing supports multithreading and is implemented using Kotlin coroutines  
- all internal operations (which files are new/changed/deleted) are based on mathematical quantity operations (intersect, union, subtract) with an easy 
customizable identity definition
- all operations can be simulated with the parameter "dryRun"
- sync und backup can be configured to ask for confirmation if a specific amount of file changes is exceeded
- designed using the fail-fast principle

Examples:

    java -jar FileIndexerAndBackup.jar  backup --db backupIndex.db  /my/sourcePath  /my/backup/targetPath
