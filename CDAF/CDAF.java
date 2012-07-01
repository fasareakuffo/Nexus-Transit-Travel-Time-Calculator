import java.io.*;
import java.util.*;
import java.sql.Timestamp;
import java.text.DateFormat;

public class CDAF {
//Full version, last edited 2 April 2012
//Compatible with GTFS

private static int i, j, k, stoplineLines, stopconvLines, tazLines, scheduleLines, transferLines, countOT, templine, templine2, tripLines;
private static double cstop_time;
private static boolean append=true, checkOT=false, firstTAZrun=true;

private static int stopline[][] = new int[20000][2]; //INCREASE AS NECESSARY
//column 0 has stop numbers
//column 1 has line numbers

private static double stopconv[][] = new double[90000][4]; //INCREASE AS NECESSARY
//column 0 has stop numbers
//column 1 has equivalent TAZ
//column 2 has walk time to/from block MAKE SURE THIS IS IN MINUTES!!!
//column 3 has block id

private static double schedule[][] = new double[920000][5]; //INCREASE AS NECESSARY
//column 0 has lineid
//column 1 has tripid
//column 2 has sequence
//column 3 has stopid
//column 4 has time in seconds (time since midnight)

private static double transfer[][] = new double[121000][4]; //INCREASE AS NECESSARY
//column 0 has line1
//column 1 has stop1
//column 2 has line2
//column 3 has stop2

private static double tempsch[][] = new double[10000][5]; //INCREASE AS NECESSARY
//column 0 has stop1
//column 1 has stop2
//column 2 has stop1_stop2 travel time in minutes
//column 3 has stop2 schedule time in seconds
//column 4 has final line number


private static double tazmatrix[][] = new double[1500000][4];
//column 0 has TAZ1
//column 1 has TAZ2
//column 2 has TAZ1_TAZ2 travel time in minutes --set to 1440 (24 hrs) initially--
//column 3 has final line number (0 indicates walk trip)

private static double trips[][] = new double[24000][2];
//column 0 has line
//column 1 has unique trip id (unique across all lines)

private static File CDAFoutput = null, tazfile;

public static void main (String[] args){
File stoplinefile, stopfile, schedulefile, transferfile, tripfile;
String temp, currentline[];

java.util.Date date= new java.util.Date();
System.out.println ("NEXUS Transit Travel Time Calculator: Version 1.3");
System.out.println ("Initializing...");

CDAF m1 = new CDAF(); //allows you to run non-static methods

stoplinefile = new File ("stopline.csv"); //input stop to line file
stoplineLines = m1.LineNo(stoplinefile);
stopfile = new File ("stopconv_block2.csv"); //input stop to taz conversion file
stopconvLines = m1.LineNo(stopfile);
schedulefile = new File ("stop_times.txt"); //input GTFS stop times file
scheduleLines = m1.LineNo(schedulefile)-1;
tripfile = new File ("trips.txt"); //input GTFS trip file
tripLines = m1.LineNo(tripfile)-1;
transferfile = new File ("transfer_2030.csv"); //input transfer file location
transferLines = m1.LineNo(transferfile);
tazfile = new File ("TAZ_TAZWWALKTIMES.csv"); //result file location
tazLines = m1.LineNo(tazfile);

CDAFoutput = new File ("CDAFoutput.txt"); //outputs stop to stop times from onetransfer to avoid array size limits
CDAFoutput.delete();

//*****bring stop-line file into array*****
try { //reads stop-line file to an array
BufferedReader scrSL = new BufferedReader(new FileReader(stoplinefile));
	for(i=0;i<stoplineLines;i++) { 
	temp = scrSL.readLine();
	currentline = temp.split(",");
		
		for(j=0;j<=1;j++) {
		
		stopline[i][j] = Integer.parseInt(currentline[j]);
		
		} //inner for
	
	} //outer for

scrSL.close();	
    } catch (Exception e) {
      System.out.println ("exception:" + e );
    } //catch
//*****stop-line file now an array*****

//*****bring stop-taz file into array*****
try { //reads stop-TAZ conversion file to an array
BufferedReader scanner = new BufferedReader(new FileReader(stopfile));
	for(i=0;i<stopconvLines;i++) {
	temp = scanner.readLine();
	currentline = temp.split(",");
	
		for(j=0;j<=3;j++) {
		stopconv[i][j] = Double.parseDouble(currentline[j]);
			
		} //inner for
	
	} //outer for

scanner.close();	
    } catch (Exception e) {
      System.out.println ("exception:" + e );
    } //catch
//*****stop-taz file now an array*****




//*****bring schedule file into array*****
try { //reads schedule file to an array
BufferedReader tripRDR = new BufferedReader(new FileReader(tripfile));
	temp = tripRDR.readLine(); //read header now or it gets confused later

	for(i=0;i<tripLines;i++) { 
	temp = tripRDR.readLine();
	currentline = temp.split(",");
	j=currentline[0].length();
	
		if(currentline[1].equals("MAR12-Multi-Weekday-01")){
			trips[i][0] = Double.parseDouble(currentline[0].substring(0,j-3));
			trips[i][1] = Double.parseDouble(currentline[2].substring(0,7));
			//System.out.println("trips: "+trips[i][0]+", "+trips[i][1]);
		} else {
		//System.out.println("service: "+currentline[1]);
		i--;
		tripLines--;
		}
					
	} //outer for
tripRDR.close();

//System.out.println("now in schedule file");
BufferedReader scrSCH = new BufferedReader(new FileReader(schedulefile));
String timeparse[];
//BufferedWriter errlog = new BufferedWriter(new FileWriter("errorlog.csv")); //logs errors

	temp = scrSCH.readLine(); //read header now or it gets confused later
	//System.out.println(temp);	
for(i=0;i<scheduleLines;i++) { 
	temp = scrSCH.readLine();
	currentline = temp.split(",");


	schedule[i][1] = Double.parseDouble(currentline[0].substring(0,7)); //tripID
	for(j=0;j<tripLines;j++){
		if(trips[j][1]==schedule[i][1]) break;
	}
		schedule[i][0]=trips[j][0]; //when match is found, save this as line number
//		System.out.println("Does "+trips[j][1]+" equal "+schedule[i][1]+"?");
	schedule[i][2]=Double.parseDouble(currentline[4]); //sequence
	schedule[i][3]=Double.parseDouble(currentline[3]); //stopID
	timeparse = currentline[2].split(":");
		schedule[i][4] = Double.parseDouble(timeparse[0])*3600+Double.parseDouble(timeparse[1])*60+Double.parseDouble(timeparse[2]); //time since midnight				
		//System.out.println("stop= "+schedule[i][3]+" time= "+schedule[i][4]);

if(schedule[i][4]>32400 || schedule[i][4]<25200 || schedule[i][1]==0){ //throw out everything that isn't between 7 and 9am
i--;
scheduleLines--;
} //if
	
	//errlog.write(schedule[i][0]+"\n");
	//errlog.flush();
} //outer for
	
scrSCH.close();
	//errlog.close();
    } catch (Exception e) {
      System.out.println ("exception:" + e );
    } //catch
//*****schedule file now an array*****



//*****bring transfer file into array*****
try { //reads transfer file to an array
BufferedReader scrTR = new BufferedReader(new FileReader(transferfile));
	for(i=0;i<transferLines;i++) { 
	temp = scrTR.readLine();
	currentline = temp.split(",");
		
		if(currentline[0] != currentline[2]){
			for(j=0;j<=3;j++) {
				transfer[i][j] = Double.parseDouble(currentline[j]);
			} //inner for
		}
	
	} //outer for

scrTR.close();	
    } catch (Exception e) {
      System.out.println ("exception:" + e );
    } //catch
//*****transfer file now an array*****


m1.clearTAZ(); //resets TAZ matrix to walk time values

//System.exit(-1); //stop here, used for debugging
double cstop, cline, cTT, tstop, tTT, kstop, kline, ktime;
try{
System.out.println ("Initialization complete. Looking at schedules...");
java.util.Date lastCheckpointTime = new java.util.Date();
double lastCheckpointPercent = 0;
for(i=0;i<stoplineLines;i++) { //cycle through stopline
	
	cstop=stopline[i][0];
	cline=stopline[i][1];
	
	cTT=0;
	double[] argument = {cstop, cline, cTT};
	m1.zerotransfer(argument);
	//---------------------------------
	//THIS BLOCK OF CODE MOVED TO ZEROTRANSFER in this version of the model
	// int jmax=templine;
	
	// for(j=0;j<jmax;j++) { //check for transfers
	// tstop=tempsch[j][1];
	// tTT=tempsch[j][2];
	// ktime=tempsch[j][3];
	//cstop_time is the schedule time in seconds at cstop
		// for(k=0;k<transferLines;k++) {
			// if (cline==transfer[k][0] && tstop==transfer[k][1]) {
				// kstop=transfer[k][3];
				// kline=transfer[k][2];
			// double[] targument = {cstop, kstop, kline, tTT, ktime};
			
			// m1.onetransfer(targument);
			// }
		// } //inner for
	// } //middle for
	
	//call TAZ method here
	// m1.TAZ(templine);
	//-----------------------------------

	// Are we an additional percentage point done?
	double currentCheckpointPercent = java.lang.Math.floor(((double)i / (double)stoplineLines) * 100);
	if (currentCheckpointPercent > lastCheckpointPercent) {
		
		// Calculate how long since the last checkpoint
		java.util.Date currentCheckpointTime = new java.util.Date();
		long elapsedSeconds = (currentCheckpointTime.getTime() - lastCheckpointTime.getTime()) / 1000;
		
		// Print out a status update
		System.out.println( currentCheckpointPercent + "% done, interval = " + elapsedSeconds);
		
		// reset the percent and time counters
		lastCheckpointTime = currentCheckpointTime;
		lastCheckpointPercent = currentCheckpointPercent;
	}
	
} //outer for

// checkOT=true;
// templine=0;

// countOT = m1.LineNo(CDAFoutput);
// m1.TAZ(templine); //call TAZ one last time, telling it to read the onetransfer output

System.out.println("100% complete! Now writing results to file");

//write results to file
int wl=0; //write line
BufferedWriter walrus = new BufferedWriter(new FileWriter("results.csv"));

while(tazmatrix[wl][0] != 0) {
//if(tazmatrix[wl][0]==256){ //remember to remove this later
walrus.write(tazmatrix[wl][0]+","+tazmatrix[wl][1]+","+tazmatrix[wl][2]+","+tazmatrix[wl][3]+"\n");
walrus.flush();
//}
wl++;
} //while
walrus.close();

System.out.println("Started at: "+new Timestamp(date.getTime()));
java.util.Date date2= new java.util.Date();
System.out.println("Ended at: "+new Timestamp(date2.getTime()));
long elapsedTime = (date2.getTime() - date.getTime()) / 1000;
System.out.println("Elapsed time: " + elapsedTime);
//System.out.println("I am the walrus");
//System.out.println(tempsch[0][0]);
//System.out.println(tempsch[2][2]);
} catch (Exception e){//Catch exception if any
	System.err.println("Error: "+e.getMessage());
}

} //main

public void zerotransfer(double[] args) {
double cstop=args[0];
double cline=args[1];
double cTT=args[2];
int a,b;
double ctrip=0;
templine=0;
//System.out.println("Looking at schedule for route "+cline);

try { //checks schedule for a match

	for(a=0;a<scheduleLines;a++) { 
	
		if (schedule[a][0]==cline && schedule[a][3]==cstop) {
			ctrip = schedule[a][1];
			cstop_time = schedule[a][4]; //schedule time in seconds at cstop
			b=a;
			
			if(cstop_time >= 25200 && cstop_time <= 28800){ //this will select for trips that start between 7:00 and 8:00
				while (schedule[b][0]==cline && schedule[b][1]==ctrip){
					tempsch[templine][0]=cstop;
					tempsch[templine][1]=schedule[b][3];
					tempsch[templine][2]=(schedule[b][4]-cstop_time);
					tempsch[templine][3]=schedule[b][4];
					tempsch[templine][4]=schedule[b][0];
					
	//				System.out.println("Time to stop "+tempsch[templine][1]+" is "+tempsch[templine][2]);
					
					templine++;
					b++;
				} //while
			
			
			//insert things here
				int jmax=templine;
				double tstop, tTT, ktime, kstop, kline;
	
				for(j=0;j<jmax;j++) { //check for transfers
				tstop=tempsch[j][1];
				tTT=tempsch[j][2];
				ktime=tempsch[j][3];
				//cstop_time is the schedule time in seconds at cstop
					for(k=0;k<transferLines;k++) {
						if (cline==transfer[k][0] && tstop==transfer[k][1]) {
							kstop=transfer[k][3];
							kline=transfer[k][2];
						double[] targument = {cstop, kstop, kline, tTT, ktime};
						
						onetransfer(targument);
						}
					} //inner for
				} //middle for
				
				//call TAZ method here
				//System.out.println("calling TAZ");
				TAZ(templine);
			//end insert
			clearTAZ();
			
			} //if
		} //if
	
	} //outer for
	
} catch (Exception e) {
    System.err.println ("Error in zerotransfer: "+e.getMessage());
} //catch

//System.out.println(cstop+"+"+cline+"+"+cTT);
} //zerotransfer

public void onetransfer(double[] args) {
double cstop=args[0]; //cstop
double kstop=args[1]; //kstop
double kline=args[2]; //kline
double cTT=args[3]; //=cstop_time schedule time at stop1
double ktime=args[4]; //ktime schedule time at transfer stop
int a, b;
double ktrip=0;
double kseq=0;
templine2=0;

//TAZ variables start
int d, g, TAZline;
double TTinmin, stop1, stop2, TAZ1, TAZ2, access=0, egress=0, OTtime, line;
//end TAZ variables

try { //checks schedule for a match
//System.out.println("Calculating transfer with route "+kline);


	for(a=0;a<scheduleLines;a++) {
//		java.util.Date startTime = new java.util.Date();
		
		//find match after arrival time but within 15 mins
		if (schedule[a][0]==kline && schedule[a][3]==kstop && schedule[a][4]>=ktime && (schedule[a][4]-ktime)<=900) {
			//System.out.println("Can transfer to line "+kline+" at stops "+cstop+" and "+kstop);
			ktrip = schedule[a][1];
			kseq = schedule[a][2];
			b=a;
			
			//copy schedule for rest of route
			while (schedule[b][0]==kline && schedule[b][1]==ktrip && schedule[b][2]>=kseq){
				
				//***start here***
					//this code was in method TAZ in the version that wrote transfer results to file
					//moved here due to file size limitations in NTFS
				
						//BufferedReader scrOT = new BufferedReader(new FileReader(CDAFoutput));
						//for(i=0;i<countOT;i++) {
						
							//temp = scrOT.readLine();
							//currentline = temp.split(",");
							
							TAZ1=0;
							TAZ2=0;
							stop1=cstop;
							stop2=schedule[b][3];
							OTtime=(schedule[b][4]-ktime+cTT);
							line=kline;
							
						
							for(d=0;d<=stopconvLines;d++){
								if (stop1==stopconv[d][0]){
									TAZ1=stopconv[d][1];
									access=stopconv[d][2]; //time to access stop from center of block
									// Why does that matter if we are transfering?
									
									for(g=0;g<=stopconvLines;g++){
										if (stop2==stopconv[g][0]){
											TAZ2=stopconv[g][1];
											egress=stopconv[g][2]; //time to reach center of block from stop
											
											TAZline=(int)(1201*(TAZ1-1)+(TAZ2-1)); //USE FOR TRANSIT SCENARIOS
											//System.out.println("TAZ1: "+TAZ1+" TAZ2: "+TAZ2);
											TTinmin=(OTtime/60)+access+egress;
											
											if (TTinmin < tazmatrix[TAZline][2] && TTinmin >= 0) {
												tazmatrix[TAZline][2]=TTinmin; //replace previous time if this is lower
												tazmatrix[TAZline][3]=line; //final line number
											}
										}
									} //g for
								}
							} //d for
						
						//} //outer for
						//scrOT.close();
				//***stop here***
				
				//templine++;
				b++;
			} //while
			
			
		} //if
	
//		java.util.Date endTime = new java.util.Date();
//		long procTime = endTime.getTime() - startTime.getTime();
//		if (procTime != 0) {
//			System.out.println("Processed schedule line " + a + " in " + procTime);
//		}
	} //outer for

} catch (Exception e) {
    System.err.println ("Error in onetransfer: "+e.getMessage());
} //catch
} //onetransfer

public void TAZ(int arg) {
int c, d, g, cmax, TAZline;
double TTinmin, stop1, stop2, TAZ1, TAZ2, access=0, egress=0, OTtime, line;
cmax=arg;
String temp, currentline[];

try{
//System.out.println("TAZ called");
FileWriter ot = new FileWriter(CDAFoutput,append);
if(firstTAZrun==true){ //initialize the output file	
	ot.write("\"stop time (TAZ ->)\",");
	
	for(i=1;i<=1201;i++){
		ot.write(i+",");
	} //for
	
	ot.write("\n");
	firstTAZrun=false; //only need to do this once
} //if

//**this section parses the results from zerotransfer**
	//System.out.println("In TAZ");
	for(c=0;c<Math.min(cmax,10000);c++) {
		TAZ1=0;
		TAZ2=0;
		stop1=tempsch[c][0];
		stop2=tempsch[c][1];
		line=tempsch[c][4];

	// System.out.println("c: "+c);
		//d=0;
		
		for(d=0;d<=stopconvLines;d++){
			
			// System.out.println("Stop: " +stop1);
	//		if (stop1==stopconv[d][0] && stopconv[d][0]!=0){
			if (stop1==stopconv[d][0]) {
				TAZ1=stopconv[d][1];
				access=stopconv[d][2]; //time to access stop from center of block
				// System.out.println("Access time from TAZ " + TAZ1 + " to stop " + stop1 + " is " + access);
				
				for(g=0;g<=stopconvLines;g++){
					if (stop2==stopconv[g][0] && stopconv[g][0]!=0){
					TAZ2=stopconv[g][1];
					
					//if(TAZ1==0){
					//System.out.println(stopconv[d][0]+","+stopconv[d][1]+","+stopconv[d][2]); }
					egress=stopconv[g][2]; //time to reach center of block from stop
					// System.out.println("Egress time from TAZ " + TAZ2 + " to stop " + stop2 + " is " + egress);

					
					TAZline=(int)(1201*(TAZ1-1)+(TAZ2-1)); //USE FOR TRANSIT SCENARIOS
					TTinmin=(tempsch[c][2]/60)+access+egress;
					if(TAZ2==348) {
						// System.out.println("Travel time to TAZ 348: " + TTinmin);
						// System.out.println("How does that compare to " + tazmatrix[TAZline][2] + "?");
					}
						
						if (TTinmin < tazmatrix[TAZline][2] && TTinmin >= 0) {
							tazmatrix[TAZline][2]=TTinmin; //replace previous time if this is lower
							tazmatrix[TAZline][3]=line; //final line number
							// System.out.println("TAZ " + TAZ1 + " to Taz " + TAZ2 + ": " + TTinmin);
							// System.out.println("Replaced with new travel time: " + TTinmin);
						}
					}
				} //g for
			}
		} //d for
		
	} //c for

ot.write(cstop_time+","); //this is the time this trip arrived at our stop of interest
for(i=502018;i<=503217;i++){ //print the accessibility for this trip to file
	ot.write(String.valueOf(tazmatrix[i][2])+",");
} //for
ot.write(String.valueOf(tazmatrix[503218][2])+"\n");
ot.flush();

} catch (Exception e) {
    System.err.println ("Error in TAZ: "+e.getMessage());
} //catch

//reset templine (not necessary to clear tempsch -- it will be written over)
templine=0;

}//TAZ

public int LineNo(File fileno) {
int count = 0;
	try{
		
		BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
		if (fileno.exists()){
			FileReader fr = new FileReader(fileno);
			LineNumberReader ln = new LineNumberReader(fr);
			
			while (ln.readLine() != null){
				count++;
			}
			ln.close();
			
		}
		else{
			System.out.println("ERROR in LineNo: File does not exist!");
		}
		
	} //try
	catch(IOException e){
		e.printStackTrace();
	} //catch
return count;
} //LineNo

public static void clearTAZ(){
String temp, currentline[];

//*****bring taz travel time matrix file into array*****
try { //reads TAZ matrix file to an array

BufferedReader scrTT = new BufferedReader(new FileReader(tazfile));
	for(i=0;i<tazLines;i++) { 
	temp = scrTT.readLine();
	currentline = temp.split(",");
	
		for(j=0;j<=2;j++) {
		tazmatrix[i][j] = Double.parseDouble(currentline[j]);
		} //inner for
	
	} //outer for

scrTT.close();	
    } catch (Exception e) {
      System.out.println ("exception:" + e );
    } //catch
//*****taz travel time matrix now an array*****

}//clearTAZ

} //class