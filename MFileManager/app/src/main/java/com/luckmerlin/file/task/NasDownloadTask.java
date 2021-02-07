package com.luckmerlin.file.task;

import com.luckmerlin.core.debug.Debug;
import com.luckmerlin.core.util.Closer;
import com.luckmerlin.file.LocalPath;
import com.luckmerlin.file.MD5;
import com.luckmerlin.file.NasPath;
import com.luckmerlin.file.Path;
import com.luckmerlin.file.api.Label;
import com.luckmerlin.file.api.What;
import com.luckmerlin.task.CodeResult;
import com.luckmerlin.task.OnTaskUpdate;
import com.luckmerlin.task.Result;
import com.luckmerlin.task.Status;
import com.luckmerlin.task.Task;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

public final class NasDownloadTask extends FileTask<NasPath,LocalPath> {
    private long mPerSecondSize;

    public NasDownloadTask(NasPath from, LocalPath to){
        super(from,to);
    }

    @Override
    protected Result onExecute(Task task, OnTaskUpdate callback) {
        final LocalPath toPath=getTo();
        final NasPath fromPath=getFrom();
        if (null==toPath||null==fromPath){
            Debug.D("Can't download while path invalid.");
            return null;
        }
        final String toPathValue=toPath.getPath();
        final String fromUriPath=fromPath.getPath();
        if (null==toPathValue||null==fromUriPath){
            Debug.D("Can't download while path value invalid."+toPath);
            return null;
        }
        final String hostPort=fromPath.getHostPort();
        final File toFile=new File(toPathValue);
        final File parent=toFile.getParentFile();
        if (null==parent){
            Debug.D("Can't download while parent is NULL.");
            return null;
        }
        notifyTaskUpdate(Status.PREPARING,callback);
        OutputStream outputStream=null;
        InputStream inputStream=null;
        HttpURLConnection connection = null;
        try {
            final Map<String,String> maps=new HashMap<>();
            maps.put(Label.LABEL_PATH,fromUriPath);
//            HttpURLConnection conn = connection=createHttpConnect(hostPort,HEAD,maps);
//            if (null==conn){
//                Debug.W("Fail create download connect to fetch file head.");
//                return null;
//            }
//            String contentType=conn.getContentType();
//            String connHeaderLength = conn.getHeaderField("content-length");
//            final long fileLength = null!=connHeaderLength&&connHeaderLength.length()>0?Long.parseLong(connHeaderLength):-1;
//            Debug.D("Head fetched of download file."+contentType+" "+fileLength+" "+fromUriPath);
//            if (fileLength<=0){
//                Debug.D("Fail download file which length is empty or EMPTY");
//                return null;
//            }
//            final FileDownloadResult succeedResult=new FileDownloadResult(fromUriPath,toPath,fileLength,contentType);
//            if (localLength>=fileLength){
//                int cover=getCover();
//                if (cover!=Cover.COVER_REPLACE) {
//                    Debug.D("File has been already downloaded. "+fileLength+" "+toPath);
////                    notifyStatus(Status.DOING, What.WHAT_NONE, "File has been already downloaded."+fileLength, progress);
//                    return null;
//                }else{
//                    toFile.delete();
//                    if (!toFile.exists()){
////                        progress.setDone(currentLength=0);
//                        Debug.D("Deleted already downloaded file while download with cover mode replace." + cover + " " + toPath);
//                    }else{
////                        notifyStatus(Status.FINISH, What.WHAT_NONE, "Fail delete already downloaded."+fileLength, succeedResult);
//                        return null;
//                    }
//                }
//                toFile.delete();
//                if (!toFile.exists()){
//                    Debug.D("Deleted already downloaded file while download with cover mode replace." + localLength + " " + toPath);
//                    localLength=0;
//                }else{
//                    Debug.W("Fail delete already downloaded."+fileLength);
//                    return null;
//                }
//            }
//            conn.disconnect();//Disconnect head connection
            final HttpURLConnection conn=connection=createHttpConnect(hostPort,GET,maps);
            if (null==conn){
                Debug.W("Fail open connection for download target path.");
                return null;
            }
            final long localLength=toFile.exists()?toFile.length():0;
            conn.setRequestProperty(Label.LABEL_FROM,Long.toString(localLength));
            conn.setRequestProperty(Label.LABEL_SIZE,Long.toString(-1));
            Debug.D("Prepared to download file from "+localLength+" "+fromUriPath);
            conn.connect();
            final int responseCode=conn.getResponseCode();
            if (responseCode!=HttpURLConnection.HTTP_OK&&responseCode!=HttpURLConnection.HTTP_PARTIAL){
                Debug.W("Fail open connection response code invalid."+responseCode);
                return null;
            }
            final String contentType=conn.getContentType();
            if (null==contentType||contentType.length()<=0||!contentType.contains("luckMerlin/file-data")){
                Debug.W("Can't download file while server response content type invalid."+contentType);
                return null;
            }
            final String fileMd5 = conn.getHeaderField(Label.LABEL_MD5);
            String fileLengthValue = conn.getHeaderField(Label.LABEL_LENGTH);
            if (null==fileLengthValue||fileLengthValue.length()<=0){
                Debug.W("Can't download file while response file md5 or length invalid."+fileLengthValue+" "+fileMd5);
                return null;
            }
            long fileLength=-1;
            try {
                fileLength=Long.parseLong(fileLengthValue);
            }catch (Exception e){
                Debug.W("Can't download file while response file length invalid."+fileLengthValue);
                return null;
            }
            if (fileLength<0){
                Debug.W("Can't download file while response file length invalid."+fileLength);
                return null;
            }
            if (!toFile.exists()){
                if (!parent.exists()) {//Make parent directory
                    Debug.D("Create download file parent directory. "+parent);
                    parent.mkdirs();
                }
                if (parent.exists()){
                    Debug.D("Create download file. "+toFile);
                    toFile.createNewFile();
                }
            }
            if (!toFile.exists()){
                Debug.D("Fail download file which create file fail. "+toFile);
                return null;
            }
            if (!toFile.canWrite()){
                Debug.D("Fail download file which target path NONE permission. "+toFile);
                return null;
            }
            if ((null==fileMd5||fileMd5.length()<=0)&&fileLength==0){//Check if empty file
                return new FileCodeResult(What.WHAT_SUCCEED);
            }
            final InputStream input=inputStream= conn.getInputStream();
            if (null==input){
                Debug.W("Stream is NULL.");
                return null;
            }
            BufferedInputStream bufferedInputStream = new BufferedInputStream(input);
            Debug.D("Downloading file "+localLength+" "+contentType+" "+toPath);
            notifyTaskUpdate(Status.EXECUTING,callback);
            OutputStream out =outputStream= new FileOutputStream(toFile,localLength<fileLength);
            long downloaded=localLength;
            int size=0;
            final byte[] buf = new byte[1024*1024];
            long lastTime=System.nanoTime();
            double sec;
            while ((size = bufferedInputStream.read(buf)) >=0) {
                if (size>0){
                    out.write(buf, 0, size);
                    long currentTime=System.nanoTime();
                    downloaded += size;
                    if ((sec=(((double) (currentTime-lastTime))/(1000000000)))>0){
                        mPerSecondSize=(long)(size/sec);
                    }
                    notifyTaskUpdate(Status.EXECUTING,callback);
                    lastTime=currentTime;
                }
                if (isCanceled()){
                    Debug.D("Canceled download file."+toPath);
                    break;
                }
            }
            out.flush();
            Debug.D("Downloaded nas file,now match file's valid."+fileMd5);
            final String md5=new MD5().getFileMD5(toFile);
            Debug.D("AAAAAAAAa "+md5);
            final long currentLength=toFile.length();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            mPerSecondSize=0;
            new Closer().close(outputStream,inputStream);
            if (null!=connection){
                connection.disconnect();
            }
        }
        Debug.W("Fail download file task."+localLength+" "+toPath);  //Download fail
        if (!isEnableBreakpoint()&&null!=toFile&&toFile.exists()){//Delete fail file
            Debug.D("Delete download fail file."+toFile.length()+" "+toPath);
            toFile.delete();
        }
        return null;
    }
}