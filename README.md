
![ScreenShot](https://github.com/tanoDxyz/GDownload/blob/main/logo.png)

Overview
--------

GDownload is a simple, powerful, easy to use, customizable file download client library for Android.  

![ScreenShot](https://github.com/tanoDxyz/GDownload/blob/main/screen_shot_main.png)
![ScreenShot](https://github.com/tanoDxyz/GDownload/blob/main/screenshot_group.png)
![ScreenShot](https://github.com/tanoDxyz/GDownload/blob/main/screenshot_single.png)

Features
--------

* Simple and easy to use API.
* Continuous downloading in the background.
* Concurrent downloading support.
* Ability to pause and resume downloads.
* Set the priority of a download.
* Network-specific downloading support.
* Ability to retry failed downloads.
* Ability to group downloads.
* Easy progress and status tracking.
* Download remaining time reporting (ETA).
* Download speed reporting.
* Download Elapsed time reporting.
* Save and Retrieve download information anytime.
* Scope Storage support.
* And more...

Prerequisites
-------------

If you are saving downloads outside of your application's sandbox, you will need to
add the following storage permissions to your application's manifest. For Android SDK version
23(M) and above, you will also need to explicitly request these permissions from the user.

```xml
<uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
```
Also, as you are going to use Internet to download files. We need to add the Internet access permissions
in the Manifest.

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

How to use GDownload
----------------

Using GDownload is easy! Just add the following to your application's root build.gradle file.

```java
 allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
 }
```
And then add the dependency to the module level build.gradle file.
```java
implementation 'com.github.tanoDxyz:GDownload:1.1'
```


### Initalization

**Although! library can be used independently without initalization** but initalization gives us two benefits.  
First, it will reuse the existing downloaders from the pool if available.  
Second, if the app is closed and the user wants to stop all the downloads running this will help out. 


```java
GDownload.init(lifecycle) //activity.lifecycle or any other lifecycle // could be null too
```
The above line will initalize the library and if the ` non null` Lifecycle  
is passed the library will terminate all the running or paused downloads when   
the lifecycle is destroyed.



### Single Download

#### First Way 
Make sure you called ``` GDownload.init(Lifecycle) ``` and then
Inside `Activity` or `Fragment` call the following method.  
Remember if the argument passed is `Activity` or `Fragment`.  
`DownloadProgressListener` callbacks will be called only when the `Activity` or `Fragment` is visible.

``` java
        GDownload.singleDownload(Activity or Fragment or Context) {
        
            url = "url"
            name = "fileName or FilePath"

            downloadProgressListener = object :DownloadProgressListener {
            
                override fun onConnectionEstablished(downloadInfo: DownloadInfo?) {}

                override fun onDownloadProgress(downloadInfo: DownloadInfo?) {}

                override fun onDownloadFailed(downloadInfo: DownloadInfo, ex: String) {}

                override fun onDownloadSuccess(downloadInfo: DownloadInfo) {}

                override fun onDownloadIsMultiConnection(
                    downloadInfo: DownloadInfo,
                    multiConnection: Boolean
                ) {}

                override fun onPause(downloadInfo: DownloadInfo, paused: Boolean, reason: String) {}

                override fun onRestart(
                    downloadInfo: DownloadInfo,
                    restarted: Boolean,
                    reason: String
                ) {}

                override fun onResume(downloadInfo: DownloadInfo, resume: Boolean, reason: String) {}

                override fun onStop(downloadInfo: DownloadInfo, stopped: Boolean, reason: String) {}
            }
        }

```

This method `GDownload.singleDownload` supports the following attributes.

| Property   | Definition |
| ------------- |:-------------:|
| url*           | resource address     |
| name*       | file name or absolute filePath     |
| downloadCallbacksOnMainThread      | Flag indicating which thread to use for download progress callbacks     |
| connectionFactory           | connection Factory used to make connections to the remote server     |
| downloadFilesRoot           | optional root path that will be used for saving files. if `name` argument is absolute path it always take precedence if both are present.     |
| networkType           | network type to use for downloading.     |
| connectionRetryCount           | number of times connection factory will try to establish a connection in case of failures.     |
| maxNumberOfConnections           | number of concurrent connections(threads). max 32     |
| progressUpdateTimeMilliSec           | interval difference between consective calls of onDownloadProgress method in DownloadProgressListener     |
| getDownloader()           | retrieve the download manager associated with the download     |
| downloadProgressListener           | Download progress Listener.     |



#### Second Way 
Make sure you called ``` GDownload.init(Lifecycle) ``` and then call the following method.  
>
``` java
GDownload.freeDownloader(Context) { downloader ->
            downloader.download(
                "url",
                "name",
                NetworkType.ALL,
                object : DownloadProgressListener {
                    override fun onDownloadSuccess(downloadInfo: DownloadInfo) {}

                    override fun onDownloadFailed(downloadInfo: DownloadInfo, ex: String) {}
                })
}

```

#### Third Way
For this way of downloading you don't need to `initalize` anything.  
Simply call the following
``` java
val downloader = GDownload.freeDownloader(
    Context,
    ScheduledBackgroundExecutor,
    DownloadCallbacksHandler,
    FileStorageHelper,
    ConnectionManager,
    DownloadDatabaseManager,
    NetworkInfoProvider
   )
   downloader.download("url", "name", NetworkType.ALL, object : DownloadProgressListener {})
```

#### Pause Download
To pause a download is pretty simple.
``` java 
downloader.freezeDownload {frozen,msg ->
            if(frozen) {
                //download froze/paused
            }else {
                // $msg indicates the reason
            }
        }
```
Remember when this method is called `DownloadProgressListener` `onPause()` is also invoked.  


#### Stop Download
To stop a download is pretty simple.
``` java 
downloader.stopDownload {stopped,msg ->
            if(stopped) {
                //download stopped
            }else {
                // $msg indicates the reason
            }
        }
```
Remember when this method is called `DownloadProgressListener` `onStop()` is also invoked.


#### Difference b/w Pause and Stop
Pause and Stop does the same job - just `stop the download`.  
The difference lies in a fact that `Pause` `Park Threads` after download is stopped and later when `resume` is called, these `threads` will be used.
While `stop` don't park threads.


#### Resume Download
Download that is `paused` can be resumed like this.
``` java
downloader.resumeDownload {resumed, msg->
    if(resumed) {
        // download successfully resumed
    }else {
       // check the $msg
    }

}
```

#### Restart Download
Download that is 'Stopped' or `Failed` can be `restarted` via 
``` java
downloader.restart {restarted, msg->
    if(restarted) {
        // download successfully restarted
    }else {
       // check the $msg
    }

}
```
#### Load Failed Downloads From Database

##### First way
Make sure you called `GDownload.init()` and then call use the following method
``` java
GDownload.loadAllInCompleteDownloadsFromDatabase(Context) {downloadList->
   // process downloads
   downloadList.forEach { download ->
      GDownload.freeDownloader(Context) {
        it.download(download, object : DownloadProgressListener {})
      }
   }
}

```
##### Second way
``` java
val dbManager = SQLiteManager.getInstance(Context)
dbManager.findDownloadByDownloadId(downloadId_int)
val allInCompleteDownloads = dbManager.findAllInCompleteDownloads()
dbManager.findDownloadByFilePath("filePath")
dbManager.close()
```
##### Third way
If you had reference to the downloader then you can directly load the failed
download from database.
``` java
downloader.loadDownloadFromDatabase(DownloadID) {loaded->}
downloader.loadDownloadFromDatabase("filePath") {loaded->}
```
#### Fourth way
if you had reference to the GroupProcessor(`Group`) then you can directly load the failed download/s from database into the group processor.
``` java
group.loadDownloadsFromDatabase {downloadsEnqueued -> }
```
#### Shutdown Downloader
If you want to shutdown downloader and it's best practice.  
just call
``` java
downloader.shutdown()
```

### Group Download
In Order to Download Multiple files and monitor them as a single entity,
GroupDownloader can be used.  
There are various ways to create one.
Make sure you called ``` GDownload.init(Lifecycle) ``` and then

``` java
val downloadList = mutableListOf<Download>()
GDownload.freeGroup(Context) {
   groupLoopTimeMilliSecs = 5_00
   getGroup()?.apply {
      start() // start group thread. as group has it's dedicated thread
      addAll(downloadList) { // enqueue downloads in the group but don't issue the start command
      startDownloads(it) // start group downloads
      }
      addGroupProgressListener(GroupListener)
   }
}
```



see sample app for more details.
The following attributes are supported for group downloader.
| Property   | Definition |
| ------------- |:-------------:|
| groupLoopTimeMilliSecs           | As each group has dedicated thread for processing Downloads.it is the time difference in milliseconds b/w two consecutive loops.     |
| concurrentDownloadsRunningCapacity       | number of concurrent downloads that group will run. Although! there is no limit on enqueuing downloads.     |
| progressCallbacksOnMainThread      | Flag indicating which thread to use for download progress callbacks     |
| urlConnectionFactory           | connection Factory used to make connections to the remote server     |
| filesSaveRootPath           | optional root path that will be used for saving files. if `name` argument is absolute path it always take precedence if both are present.     |
| networkType           | network type to use for downloading.     |
| connectionRetryCount           | number of times connection factory will try to establish a connection in case of failures.     |
| maxConnectionPerDownload           | number of concurrent connections(threads). max 32     |
| progressUpdateTimeMilliSecs           | interval difference between consective calls of onDownloadProgress method in DownloadProgressListener     |
| getGroup()           | retrieve the group download manager associated with the downloads list.     |
| progressCallbackLifeCycle           | Lifecycle to associate the progress callbacks for individual downloads.     |
| databaseManager  | Database manager used for loading or accessing Downloads data |
| networkInfoProvider | network information provider |


#### Shutdown Group Downloader
``` java
group.shutDown()
```
#### Pause Group Download
To pause or freeze download running inside group processor.
``` java
group.freezeDownload(DownloadID)
```

#### Resume Group Download
To resume download running inside group processor.
``` java
group.resumeDownload(DownloadID)
```

#### Stop Group Download
To stop download running inside group processor.
``` java
group.stopDownload(DownloadID)
```

#### Restart Group Download
To restart download running inside group processor.
``` java
group.restartDownload(DownloadID)
```

#### Group Progress Listener
In order to track group processor progress.
attach the listener as below.
``` java
group.addGroupProgressListener(object : GroupListener {
            override fun onAdded(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

            override fun onDownloading(
                groupId: Long,
                download: DownloadInfo,
                groupState: GroupState
            ) {}

            override fun onEnqueued(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

            override fun onFailure(
                groupId: Long,
                errorMessage: String?,
                download: DownloadInfo,
                groupState: GroupState
            ) {}

            override fun onPaused(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

            override fun onStarting(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

            override fun onStopped(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

            override fun onSuccess(groupId: Long, download: DownloadInfo, groupState: GroupState) {}

            override fun onWaitingForTurn(
                groupId: Long,
                download: DownloadInfo,
                groupState: GroupState
            ) {}
        })

```


Connection Handler
------------------

By default GDownload uses the HttpUrlConnection for connecting and downloading.
In order to change or use custom Connection Handler.
you need to check out `URLConnectionHandler` and `DefaultURLConnectionHandler`.


⚠️Remember⚠️  
----------------
Library is lifecycle aware. it has its benefits but also
		   some pitfalls.  
		   Imagine if you init the library by passing the lifecycle which is associated with fragment and app stays for too long  
           in the background and for some reasons your activity,fragment or
          lifecycle is destroyed but app is not.
          in such case you might not be able to schedule further downloads
          as the background executors are killed.
          Either reinitialize the library or be careful while passing 
          the lifecycle. 
          Second scenario will be if you attach lifecycle to single or 
          group download manager and that lifecycle is destroyed or that component is destroyed. it is likely that you will not recieve progress callbacks.  
          so be careful  
          there is an activity named as `SingleDownloadLifecycleSurvivalActivity`
          which shows how to handle lifecycle events while keeping the downloads running.  
          **passing lifecycle is not necessary** 
          
Contribute
----------
GDownload can only get better if you make code contributions. Found a bug? Report it.
Have a feature idea you'd love to see in GDownload? Contribute to the project!

License
-------

```
Copyright (C) 2022 Tanveer Hussain.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
