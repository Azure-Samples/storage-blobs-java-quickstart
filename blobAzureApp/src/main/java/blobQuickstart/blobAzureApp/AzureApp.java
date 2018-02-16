// MIT License
// Copyright (c) Microsoft Corporation. All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE

package blobQuickstart.blobAzureApp;


import com.microsoft.azure.storage.blob.*;
import com.microsoft.azure.storage.models.Blob;
import com.microsoft.azure.storage.models.PublicAccessType;
import com.microsoft.rest.v2.util.FlowableUtil;

import io.reactivex.Flowable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.security.InvalidKeyException;
import java.util.Scanner;

/* *************************************************************************************************************************
* Summary: This application demonstrates how to use the Blob Storage service.
* It does so by creating a container, creating a file, then uploading that file, listing all files in a container, 
* and downloading the file. Then it deletes all the resources it created
* 
* Documentation References:
* Associated Article - https://docs.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java
* What is a Storage Account - http://azure.microsoft.com/en-us/documentation/articles/storage-whatis-account/
* Getting Started with Blobs - http://azure.microsoft.com/en-us/documentation/articles/storage-dotnet-how-to-use-blobs/
* Blob Service Concepts - http://msdn.microsoft.com/en-us/library/dd179376.aspx 
* Blob Service REST API - http://msdn.microsoft.com/en-us/library/dd135733.aspx
* *************************************************************************************************************************
*/
public class AzureApp 
{
    /* *************************************************************************************************************************
    * Instructions: Update the storageAccountName & storageAccountKey variables and then run the sample.
    * *************************************************************************************************************************
    */
    private static final String storageAccountName = "<storage-account-name>";
    private static final String storageAccountKey = "<storage-account-key>";

    public static void main( String[] args ) throws InvalidKeyException, IOException, InterruptedException
    {
        System.out.println("Azure Blob storage quick start sample");
        Scanner sc = new Scanner(System.in);
        
        SharedKeyCredentials creds = new SharedKeyCredentials(storageAccountName, storageAccountKey);
        
        // Create a service reference with the blob endpoint and default pipeline
        ServiceURL service = new ServiceURL(
                new URL(String.format("https://%s.blob.core.windows.net", storageAccountName)),
                StorageURL.createPipeline(creds, new PipelineOptions()));
        
        // Create a container reference
        String containerName = "quickstartcontainer";
        ContainerURL container = service.createContainerURL(containerName);
        
        // Creating a sample file
        File sourceFile = File.createTempFile("sampleFile", ".txt");
        System.out.println("Creating a sample file at: " + sourceFile.toString());
        Writer output = new BufferedWriter(new FileWriter(sourceFile));
        output.write("Hello Azure!");
        output.close();

        try {
            // Create the container if it does not exist with public access.
            System.out.println("Creating container: " + container.toURL());
            container.create(null, PublicAccessType.CONTAINER)
            	.subscribe(
            			resp -> System.out.println("Container created: " + containerName),
            			err -> System.err.println("An error occurred: " + err.getMessage()));
            
            System.out.println("Press the 'Enter' key when container is created");
            sc.nextLine();

            // Getting a blob reference
            BlockBlobURL blob = container.createBlockBlobURL(sourceFile.getName());

            // Creating blob and uploading file to it
            System.out.println("Uploading the sample file: " + sourceFile.getAbsolutePath());
            AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(sourceFile.toPath());
            Flowable<ByteBuffer> stream = FlowableUtil.readFile(fileChannel);
            
            blob.putBlob(stream, sourceFile.length(), null, null, null)
                .doFinally(fileChannel::close)
                .subscribe(
                        success -> System.out.println("Uploaded: " + sourceFile.getAbsolutePath()),
                        error -> System.out.println("An error occurred: " + error.getMessage()));
            
            System.out.println("Press the 'Enter' key when sample file is uploaded");
            sc.nextLine();
            
            // Listing contents of the container
            container.listBlobs(null, null)
                .subscribe(
                        res -> {
                            for(Blob b : res.body().blobs().blob()) {
                                System.out.println("Blob: " + b.name());
                            }
                        },
                        err -> System.err.println("An error occurred: " + err.getMessage()));
            
            System.out.println("Press the 'Enter' key when all blobs are listed");
            sc.nextLine();
    
            // Download blob. In most cases, you would have to retrieve the reference
            // to cloudBlockBlob here. However, we created that reference earlier, and 
            // haven't changed the blob we're interested in, so we can reuse it. 
            // Here we are creating a new file to download to.
            final File downloadedFile = new File(sourceFile.getParentFile(), "downloadedFile.txt");
            
            blob.getBlob(new BlobRange(0, sourceFile.length()), null, false)
                .flatMap(res -> FlowableUtil.collectBytesInArray(res.body()))
                .subscribe(
                        body -> {
                            Files.write(FileSystems.getDefault().getPath(downloadedFile.getAbsolutePath()), body);
                        },
                        err  -> System.err.println("An error occurred: " + err.getMessage())); 
            
            System.out.println("Press the 'Enter' key when the blob is downloaded to " + downloadedFile.getAbsolutePath());
            sc.nextLine();
            
            downloadedFile.deleteOnExit();
        } 
        finally 
        {
            System.out.println("The program has completed successfully.");
            System.out.println("Deleting the container");
            if(container != null) {
                container.delete(null)
                    .subscribe(
                            resp -> System.out.println("Container deleted."),
                            err -> System.err.println("An error occurred: " + err.getMessage()));
            }

            System.out.println("Deleting the source file");

            if(sourceFile != null)
                sourceFile.deleteOnExit();

            //Closing scanner
            sc.close();
            
            Thread.sleep(1000); // Hopefully the container is deleted
            
            System.exit(0);
        }
    }
}
