Overview

This application implements, in Java, an emulator for the NorthStar Horizon S-100 bus based Z-80 computer system from the late 1970's. This includes support for the ScreenSplitter video card that was sold by what was then Micro Diversions and later Scion corporation.

Here is some history about NorthStar computers: https://en.wikipedia.org/wiki/North_Star_Computers

And some very basic information about the ScreenSplitter: http://www.s100computers.com/Hardware%20Folder/Scion/History/History.htm

Assuming you have Maven installed, you can build the emulator from within the northstar.horizon directory like this:

mvn clean package

Then run it using something like this:

java -cp ./net.guma.northstar.horizon-1.0-jar-with-dependencies.jar net.guma.northstar.horizon.

You must have a boot disk image in drive 1 to be able to use the emulator. If no image is mounted when booting, then the emulator will automatically switch to a paused mode until you correct this and reboot, or un-pause manually (with less than useful results).

While you can boot any NorthStar compatible 'NSI' format disk, unless you boot with one that has output support for the ScreenSplitter, you will only be able to use the text console.

Included are two boot disk images, NSDOS51S_SS.NSI and NSDOS50D_SS.NSI, one single-density the other double, containing the appropriate I/O code to support both the ScreenSplitter and the text console concurrently.

Also included is a GAMES disk, containing one program, a reworking of the 1970's mainframe text-based Star Trek game, but fully using the ScreenSplitter interface and library package to present all systems, such as long and short range scanners, status report, etc. in individual windows at the same time, all updated automatically each turn. If you mount that disk on unit #2, you can run this program once you are at the North Star DOS prompt by typing the following:

GO BASIC
LOAD TREK,2
RUN

Finally in the Doc folder is a PDF of the original ScreenSplitter manual, a screen capture of the Star Trek game running, and the assembly code for the I/O routines that support the ScreenSplitter.

The menus
System
*	Reboot - reboots the emulator, loading and running whatever boot disk is in unit 1.
*	Auto Commit Writes - if checked, any change to those disks that are not marked ""Read Only" will occur immediately, so use caution.
*	Full Speed - if checked, emulation will run as fast as possible, otherwise it will run close to the 4mhz original Z-80 processor speed.
*	Paused - pauses execution if checked. This will be checked automatically if you try to boot without a disk in unit 1.
Disks (turns red until there is no disk activity for more than 5 seconds)
*	Disk 1 - is the boot disk, allowing you to mount or unmount a disk image, as well as set if it is read/only or not.
*	Disk 2-3/4 - are other disk images. Only the double density controller supports 4 drives, the single-density ends at 3.
*	Create Disk - allows creating a blank disk image of one of the 3 types: single-sided/single-density, single-sided/double-density or double-sided/double-density.
Display
*	ScreenSplitter - controls options for the ScreenSplitter display, including which of two default character sets to use (Graphics or Scientific), color options for the video window, as well as which of three pixel sizes (small, medium, or large) to use.
*	Text Console - Allows changing the colors, font type and size for the text console window
Help
*	Contains basic information about the emulator, including this dialog.
