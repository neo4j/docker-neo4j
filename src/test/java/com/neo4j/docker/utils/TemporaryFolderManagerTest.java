package com.neo4j.docker.utils;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// This is a test for a test utility. It does not actually test anything to do with the docker image.
// This is disabled unless we're actually trying to develop/fix the TemporaryFolderManager utility.
@Disabled
class TemporaryFolderManagerTest
{
    @Test
    void createsTempFolder( @TempDir Path outFolder) throws IOException
    {
        String folderPrefix = "TemporaryFolderManagerTest_createsTempFolder";
        TemporaryFolderManager manager = new TemporaryFolderManager(outFolder);
        Path p = manager.createTempFolder( folderPrefix );
        verifyTempFolder( p, folderPrefix, outFolder );
    }

    @Test
    void createsTempFolderUnderGivenParent( @TempDir Path outFolder ) throws IOException
    {
        String folderPrefix = "TemporaryFolderManagerTest_createsTempFolder";
        Path unusedFolder = outFolder.resolve( "unused" );
        Files.createDirectories( unusedFolder );

        TemporaryFolderManager manager = new TemporaryFolderManager(unusedFolder);
        Path p = manager.createTempFolder( folderPrefix, outFolder );
        verifyTempFolder( p, folderPrefix, outFolder );
        // should NOT have created something under unusedFolder
        List<Path> f = Files.list( unusedFolder ).toList();
        Assertions.assertEquals( 0, f.size(),
                                 "Folder should not have been created under "+unusedFolder );
    }

    @Test
    void createsTempFoldersWithDifferentNames( @TempDir Path outFolder) throws IOException
    {
        String folderPrefix = "TemporaryFolderManagerTest_createsTempFoldersWithDifferentNames";
        TemporaryFolderManager manager = new TemporaryFolderManager(outFolder);
        Path p1 = manager.createTempFolder( folderPrefix );
        verifyTempFolder( p1, folderPrefix, outFolder );
        Path p2 = manager.createTempFolder( folderPrefix );
        verifyTempFolder( p2, folderPrefix, outFolder );
        // There is a 1 in 10000 chance this will fail since we expect
        // the folders to have different 4 digit salt on the end of their name.
        Assertions.assertNotEquals( p1, p2, "Temp folders were created on top of each other." );
    }

    @Test
    void canSetFolderOwnerTo7474ThenCleanup( @TempDir Path outFolder) throws Exception
    {
        String folderPrefix = "TemporaryFolderManagerTest_canSetFolderOwnerTo7474ThenCleanup";
        TemporaryFolderManager manager = new TemporaryFolderManager(outFolder);
        Path tempFolder = manager.createTempFolder( folderPrefix );
        verifyTempFolder( tempFolder, folderPrefix, outFolder );
        Files.write( tempFolder.resolve( "testfile" ), "words".getBytes() );

        manager.setFolderOwnerToNeo4j( tempFolder );
        // verify expected folder owner
        Integer fileUID = (Integer) Files.getAttribute( tempFolder, "unix:uid" );
        Assertions.assertEquals( 7474, fileUID.intValue(),
                                 "Did not successfully set the owner of "+tempFolder );
        // clean up and verify successfully cleaned up
        manager.afterAll( null );
        Assertions.assertFalse( tempFolder.toFile().exists(), "Did not successfully delete "+tempFolder );
    }

    @Test
    void canCreateAndCleanupFoldersWithDifferentOwners( @TempDir Path outFolder) throws Exception
    {
        String folderPrefix = "TemporaryFolderManagerTest_canCreateAndCleanupFoldersWithDifferentOwners";
        TemporaryFolderManager manager = new TemporaryFolderManager(outFolder);
        Path tempFolder7474 = manager.createTempFolder( folderPrefix );
        Path tempFolderNormal = manager.createTempFolder( folderPrefix );
        Files.write( tempFolder7474.resolve( "testfile" ), "words".getBytes() );
        Files.write( tempFolderNormal.resolve( "testfile" ), "words".getBytes() );

        manager.setFolderOwnerToNeo4j( tempFolder7474 );

        Integer fileUID = (Integer) Files.getAttribute( tempFolder7474, "unix:uid" );
        Assertions.assertEquals( 7474, fileUID.intValue(),
                                 "Did not successfully set the owner of "+tempFolder7474 );

        manager.afterAll( null );
        Assertions.assertFalse( tempFolderNormal.toFile().exists(), "Did not successfully delete "+tempFolderNormal );
        Assertions.assertFalse( tempFolder7474.toFile().exists(), "Did not successfully delete "+tempFolder7474 );
    }

    @Test
    void shouldMountFolderToContainer(@TempDir Path outFolder) throws Exception
    {
        String folderPrefix = "TemporaryFolderManagerTest_shouldMountFolderToContainer";
        Path tempFolder;
        TemporaryFolderManager manager = new TemporaryFolderManager( outFolder );

        try(GenericContainer container = makeContainer())
        {
            tempFolder = manager.createTempFolderAndMountAsVolume( container, folderPrefix, "/root" );
            container.start();
            container.execInContainer( "touch", "/root/testout" );
            String files = container.execInContainer( "ls", "/root" ).getStdout();
            // sanity check that /root/testout actually was created
            Assertions.assertTrue( files.contains( "testout" ),
                                   "did not manage to create file inside container in the mounted folder." );
        }
        verifyTempFolder( tempFolder, folderPrefix, outFolder );
        Assertions.assertTrue( tempFolder.resolve( "testout" ).toFile().exists(),
                               "Test file was created in container but not in mounted folder. " +
                               "Probably it was unsuccessfully mounted" );
    }

    @Test
    void createsTarOfTempFolder(@TempDir Path outFolder) throws Exception
    {
        String folderPrefix = "TemporaryFolderManagerTest_createsTarOfTempFolder";
        String expectedFileContents = "words words words";
        TemporaryFolderManager manager = new TemporaryFolderManager( outFolder );

        // create one folder with one file to be zipped.
        Path tempFolder = manager.createTempFolder( folderPrefix );
        Files.write( tempFolder.resolve( "testfile" ), expectedFileContents.getBytes() );
        Assertions.assertTrue( tempFolder.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder);

        manager.afterAll( null );

        File expectedTar = new File( tempFolder.toAbsolutePath().toString()+".tar.gz" );
        Assertions.assertTrue( expectedTar.exists(), "Did not create archive "+expectedTar );
        List<String> files = listFilesInTar( expectedTar );
        Assertions.assertEquals( 1, files.size(),
                               "Tar file "+expectedTar+" exists but is empty." );
        String writtenFile = readFileInTar( expectedTar, tempFolder.getFileName()+"/testfile" );
        Assertions.assertEquals( expectedFileContents, writtenFile );
        // all temporary folder should now be deleted
        Assertions.assertFalse( tempFolder.toFile().exists(), "Temporary folder should have been deleted" );
    }

    @Test
    void createsTarOfTempFolder_2Files(@TempDir Path outFolder) throws Exception
    {
        String folderPrefix = "TemporaryFolderManagerTest_createsTarOfTempFolder_2Files";
        String expectedFileContents1 = "words1 words1 words1";
        String expectedFileContents2 = "words2 words2 words2";
        TemporaryFolderManager manager = new TemporaryFolderManager( outFolder );

        // create one folder with one file to be zipped.
        Path tempFolder = manager.createTempFolder( folderPrefix );
        Files.write( tempFolder.resolve( "testfile1" ), expectedFileContents1.getBytes() );
        Assertions.assertTrue( tempFolder.resolve( "testfile1" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder);
        Files.write( tempFolder.resolve( "testfile2" ), expectedFileContents2.getBytes() );
        Assertions.assertTrue( tempFolder.resolve( "testfile2" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder);

        manager.afterAll( null );

        File expectedTar = new File( tempFolder.toAbsolutePath().toString()+".tar.gz" );
        Assertions.assertTrue( expectedTar.exists(), "Did not create archive "+expectedTar );
        List<String> files = listFilesInTar( expectedTar );
        Assertions.assertEquals( 2, files.size(),
                                 "Tar file "+expectedTar+" exists but is empty." );
        String writtenFile1 = readFileInTar( expectedTar, tempFolder.getFileName()+"/testfile1" );
        String writtenFile2 = readFileInTar( expectedTar, tempFolder.getFileName()+"/testfile2" );
        Assertions.assertEquals( expectedFileContents1, writtenFile1 );
        Assertions.assertEquals( expectedFileContents2, writtenFile2 );
        Assertions.assertFalse( tempFolder.toFile().exists(), "Temporary folder should have been deleted" );
    }

    @Test
    void createsTarOfTempFolder_2Folders(@TempDir Path outFolder) throws Exception
    {
        String folderPrefix = "TemporaryFolderManagerTest_createsTarOfTempFolder_2Folders";
        String expectedFileContents1 = "words1 words1 words1";
        String expectedFileContents2 = "words2 words2 words2";
        TemporaryFolderManager manager = new TemporaryFolderManager( outFolder );

        // create one folder with one file to be zipped.
        Path tempFolder1 = manager.createTempFolder( folderPrefix );
        Files.write( tempFolder1.resolve( "testfile" ), expectedFileContents1.getBytes() );
        Assertions.assertTrue( tempFolder1.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder1);
        Path tempFolder2 = manager.createTempFolder( folderPrefix );
        Files.write( tempFolder2.resolve( "testfile" ), expectedFileContents2.getBytes() );
        Assertions.assertTrue( tempFolder2.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder2);

        manager.afterAll( null );

        File expectedTar1 = new File( tempFolder1.toAbsolutePath().toString()+".tar.gz" );
        File expectedTar2 = new File( tempFolder2.toAbsolutePath().toString()+".tar.gz" );
        Assertions.assertTrue( expectedTar1.exists(), "Did not create archive "+expectedTar1 );
        Assertions.assertTrue( expectedTar2.exists(), "Did not create archive "+expectedTar2 );
        List<String> files1 = listFilesInTar( expectedTar1 );
        Assertions.assertEquals( 1, files1.size(),
                                 "Tar file "+expectedTar1+" exists but is empty." );
        String writtenFile1 = readFileInTar( expectedTar1, tempFolder1.getFileName()+"/testfile" );
        Assertions.assertEquals( expectedFileContents1, writtenFile1 );
        List<String> files2 = listFilesInTar( expectedTar1 );
        Assertions.assertEquals( 1, files2.size(),
                                 "Tar file "+expectedTar2+" exists but is empty." );
        String writtenFile2 = readFileInTar( expectedTar2, tempFolder2.getFileName()+"/testfile" );
        Assertions.assertEquals( expectedFileContents2, writtenFile2 );
        Assertions.assertFalse( tempFolder1.toFile().exists(), "Temporary folder should have been deleted" );
        Assertions.assertFalse( tempFolder2.toFile().exists(), "Temporary folder should have been deleted" );
    }

    @Test
    void createsTarOfTempFolder_nestedFolders(@TempDir Path outFolder) throws Exception
    {
        // creating folders:
        // tempFolder1
        // |  tempFolder2
        // |  | testfile
        // expecting zip of tempFolder1 containing that file structure.
        String folderPrefix = "TemporaryFolderManagerTest_createsTarOfTempFolder_nestedFolders";
        String expectedFileContents = "words words words";
        TemporaryFolderManager manager = new TemporaryFolderManager( outFolder );

        // create one folder with one file to be zipped.
        Path tempFolder1 = manager.createTempFolder( folderPrefix );
        Path tempFolder2 = manager.createTempFolder( folderPrefix, tempFolder1 );
        Files.write( tempFolder2.resolve( "testfile" ), expectedFileContents.getBytes() );
        Assertions.assertTrue( tempFolder2.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder2);

        manager.afterAll( null );

        File expectedTar = new File( tempFolder1.toAbsolutePath().toString()+".tar.gz" );
        Assertions.assertTrue( expectedTar.exists(), "Did not create archive "+expectedTar );
        List<String> files = listFilesInTar( expectedTar );
        Assertions.assertEquals( 1, files.size(),
                               "Tar file "+expectedTar+" exists but is empty." );
        String writtenFile = readFileInTar( expectedTar,
                                            tempFolder1.getFileName() +"/" +
                                            tempFolder2.getFileName()+"/testfile" );
        Assertions.assertEquals( expectedFileContents, writtenFile );
        Assertions.assertFalse( tempFolder1.toFile().exists(), "Temporary folder should have been deleted" );
    }

    private void verifyTempFolder( Path tempFolder, String expectedPrefix, Path expectedParent )
    {
        Assertions.assertTrue( tempFolder.toFile().exists(), "createTempFolder did not create anything" );
        Assertions.assertTrue( tempFolder.toFile().isDirectory(), "Did not create a directory" );
        Assertions.assertTrue( tempFolder.getParent().equals( expectedParent ),
                               "Did not create temp folder under expected parent location. Actual: "+tempFolder.getParent() );
        Assertions.assertTrue( tempFolder.getFileName().toString().matches( expectedPrefix+"\\d{4}" ),
                               "did not create file with the expected folder prefix or random salt in name. " +
                               "Actual folder created: "+ tempFolder.getFileName());
    }

    private GenericContainer makeContainer()
    {
        // we don't want to test the neo4j container, just use a generic container debian to check mounting.
        // using nginx here just because there is a straightforward way of waiting for it to be ready
        GenericContainer container = new GenericContainer(DockerImageName.parse("nginx:latest"))
                .withExposedPorts(80)
                .waitingFor(Wait.forHttp("/").withStartupTimeout( Duration.ofSeconds( 5 ) ));
        return container;
    }

    private List<String> listFilesInTar(File tar) throws IOException
    {
        List<String> files = new ArrayList<>();
        ArchiveInputStream in = new TarArchiveInputStream(
                new GzipCompressorInputStream( new FileInputStream(tar) ));
        ArchiveEntry entry = in.getNextEntry();
        while(entry != null)
        {
            files.add( entry.getName() );
            entry = in.getNextEntry();
        }
        in.close();
        return files;
    }

    private String readFileInTar(File tar, String internalFilePath) throws IOException
    {
        String fileContents = null;
        ArchiveInputStream in = new TarArchiveInputStream(
                new GzipCompressorInputStream( new FileInputStream(tar) ));
        ArchiveEntry entry = in.getNextEntry();
        while(entry != null)
        {
            if(entry.getName().equals( internalFilePath ))
            {
                ByteArrayOutputStream outStream = new ByteArrayOutputStream();
                IOUtils.copy(in, outStream);
                fileContents = outStream.toString();
                break;
            }
            entry = in.getNextEntry();
        }
        in.close();
        Assertions.assertNotNull( fileContents, "Could not extract file "+internalFilePath+" from "+tar);
        return fileContents;
    }
}