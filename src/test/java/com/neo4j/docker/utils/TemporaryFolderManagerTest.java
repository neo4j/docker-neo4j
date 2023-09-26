package com.neo4j.docker.utils;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// This is a test for a test utility. It does not actually test anything to do with the docker image.
// This is disabled unless we're actually trying to develop/fix the TemporaryFolderManager utility.
@Disabled
class TemporaryFolderManagerTest
{
    @Order( 0 )
    @TempDir
    static Path outFolder;

    @Order( 1 )
    @RegisterExtension
    public TemporaryFolderManager manager = new TemporaryFolderManager(outFolder);

    @AfterEach
    void clearAnyCleanupFlags()
    {
        // some tests may leave folders remaining that are flagged for cleanup, which can affect the
        // tests that check that folders are flagged for cleanup. This will reset the flags after each test.
        manager.toCompressAfterAll.clear();
    }

    // TEST AUTOGENERATES SENSIBLE FOLDER NAME FOR EACH UNIT TEST METHOD

    @Test
    void shouldDeriveFolderNameFromTestMethodName()
    {
        String expectedMethodNameFolderPrefix = this.getClass().getName() + "_shouldDeriveFolderNameFromTestMethodName";
        String actualMethodFolderName = manager.methodOutputFolder.getFileName().toString();
        // should generate folder with similar/the same name as the method's reference and add 4 random digits to the end
        Assertions.assertTrue( actualMethodFolderName.startsWith( expectedMethodNameFolderPrefix ),
                               "Did not generate correct temporary folder name from unit test method");

        // verify salt is added to foldername like <NAME>_1234
        Assertions.assertEquals( expectedMethodNameFolderPrefix.length()+5, actualMethodFolderName.length(),
                                 "Did not correctly add 4 random digits to the expected folder name");
        String salt = actualMethodFolderName.substring( expectedMethodNameFolderPrefix.length() + 1 );
        Assertions.assertDoesNotThrow( ()->Integer.parseInt( salt ),
                                       "Folder name salt was not digits. Actual: " + actualMethodFolderName );

        // folder should not exist until we call a createTempFolder* method
        Assertions.assertFalse( manager.methodOutputFolder.toFile().exists(),
                                "Unit test method folder was created before requesting any folder creation.");
    }

    @ParameterizedTest
    @ValueSource(ints = {4,5,6})
    void parameterisedTestMethodsCreateDifferentFolders_unnamedInt(int parameter) throws IOException
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() +
                                               "_parameterisedTestMethodsCreateDifferentFolders_unnamedInt" +
                                               "_\\[" + (parameter-3) + "\\]_" + parameter + "_\\d{4}";
        verifyParameterisedFolderNaming(expectedMethodNameFolderRegex);
    }

    @ParameterizedTest(name = "name={0}")
    @ValueSource(ints = {7,8,9})
    void parameterisedTestMethodsCreateDifferentFolders_namedInt(int parameter) throws IOException
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() +
                                               "_parameterisedTestMethodsCreateDifferentFolders_namedInt_name="
                                               + parameter + "_\\d{4}";
        verifyParameterisedFolderNaming(expectedMethodNameFolderRegex);
    }

    @ParameterizedTest(name = "name={0}")
    @ValueSource(booleans = {true, false})
    void parameterisedTestMethodsCreateDifferentFolders_namedBoolean(boolean parameter) throws IOException
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() +
                                               "_parameterisedTestMethodsCreateDifferentFolders" +
                                               "_namedBoolean_name=" + parameter + "_\\d{4}";
        verifyParameterisedFolderNaming(expectedMethodNameFolderRegex);
    }

    @ParameterizedTest( name = "bool1={0} bool2={1}" )
    @CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void parameterisedTestMethodsCreateDifferentFolders_twoNamedBooleans(boolean parameter1, boolean parameter2) throws IOException
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() +
                                               "_parameterisedTestMethodsCreateDifferentFolders" +
                                               "_twoNamedBooleans_bool1=" + parameter1 +
                                               "_bool2=" + parameter2 + "_\\d{4}";
        verifyParameterisedFolderNaming(expectedMethodNameFolderRegex);
    }

    private void verifyParameterisedFolderNaming(String expectedMethodNameFolderRegex) throws IOException
    {
        // get methodFolderName from TemporaryFolderManager
        String actualMethodFolderName = manager.methodOutputFolder.getFileName().toString();
        Assertions.assertTrue( Pattern.matches( expectedMethodNameFolderRegex, actualMethodFolderName ),
                               "Folder \"" + actualMethodFolderName +
                               "\" does not match expected regex \"" + expectedMethodNameFolderRegex + "\"");
        // folder should not yet exist
        Path expectedUnitTestMethodFolder = outFolder.resolve( manager.methodOutputFolder );
        Assertions.assertFalse( expectedUnitTestMethodFolder.toFile().exists(),
                                "Folder "+expectedUnitTestMethodFolder + " should not have been created" );
        // now create folder
        manager.createFolder( "somename" );
        Assertions.assertTrue( expectedUnitTestMethodFolder.toFile().exists(),
                               "Folder "+expectedUnitTestMethodFolder + " should have been created" );
    }

    @ParameterizedTest
    @CsvSource({"/conf,conf", "/data,data", "/import,import", "/logs,logs", "/metrics,metrics", "/plugins,plugins",
            "/run/something,run_something", "/place/with space,place_with_space", "/with space,with_space"})
    void autoGeneratesSensibleFolderNameFromMountPoint(String mountPoint, String expectedFolderName)
    {
        Assertions.assertEquals( expectedFolderName, manager.getFolderNameFromMountPoint( mountPoint),
                                 "Did not autogenerate expected name from given mount point");
    }

    // TEST ACTUAL FOLDER CREATION AND MOUNTING

    @Test
    void shouldMountAnyFolderToContainer(@TempDir Path tempFolder) throws Exception
    {
        try(GenericContainer container = makeContainer())
        {
            manager.mountHostFolderAsVolume( container, tempFolder, "/root" );
            container.start();
            container.execInContainer( "touch", "/root/testout" );
            String files = container.execInContainer( "ls", "/root" ).getStdout();
            // sanity check that /root/testout actually was created
            Assertions.assertTrue( files.contains( "testout" ),
                                   "did not manage to create file inside container in the mounted folder." );
        }
        Assertions.assertTrue( tempFolder.resolve( "testout" ).toFile().exists(),
                               "Test file was created in container but not in mounted folder. " +
                               "Probably it was unsuccessfully mounted" );
    }

    @Test
    void createsFolder() throws Exception
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() + "_createsFolder_\\d{4}";
        String folderName = "somefolder";
        // first verify that no folder exists until we create something
        List<Path> allFolders = Files.list( outFolder )
                                     .filter( path -> path.getFileName()
                                                          .toString()
                                                          .matches( expectedMethodNameFolderRegex ))
                                     .toList();
        Assertions.assertEquals( 0, allFolders.size(), "A folder matching " + expectedMethodNameFolderRegex +
                                                       " was created when it should not have been");

        // now create a folder
        Path p = manager.createFolder( folderName );
        // verify folder exists, and is located at outFolder > METHODNAME > somefolder
        Path methodNameFolder = verifyMethodNameFolderExistsAndIsUnique( expectedMethodNameFolderRegex );
        verifyTempFolder( p, folderName, methodNameFolder );
    }

    @Test
    void createsFolderUnderGivenParent() throws Exception
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() + "_createsFolderUnderGivenParent_\\d{4}";
        Path unusedFolder = manager.createFolder( "somefolder1" );
        Path expectedParent = manager.createFolder( "somefolder2" );
        Path p = manager.createFolder( "somefolder3", expectedParent);

        Path methodNameFolder = verifyMethodNameFolderExistsAndIsUnique( expectedMethodNameFolderRegex );
        verifyTempFolder( unusedFolder, "somefolder1", methodNameFolder );
        verifyTempFolder( expectedParent, "somefolder2", methodNameFolder );
        verifyTempFolder( p, "somefolder3", expectedParent );

        // should NOT have created something under unusedFolder
        List<Path> f = Files.list( unusedFolder ).toList();
        Assertions.assertEquals( 0, f.size(),
                                 "Folder should not have been created under "+unusedFolder );
    }

    @Test
    void doesNotCreateFolderOutsideRoot(@TempDir Path nonRootFolder)
    {
        Assertions.assertThrows( IOException.class,
                                 () -> manager.createFolder( "somefolder", nonRootFolder),
                                 "Created a test folder outside the expected area");
    }

    @Test
    void createNamedFolderAndMount() throws Exception
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() + "_createNamedFolderAndMount_\\d{4}";
        String expectedFolderName = "aFolder";
        Path actualTempFolder;
        try(GenericContainer container = makeContainer())
        {
            actualTempFolder = manager.createNamedFolderAndMountAsVolume( container, expectedFolderName, "/root" );
            container.start();
            container.execInContainer( "touch", "/root/testout" );
            String files = container.execInContainer( "ls", "/root" ).getStdout();
            // sanity check that /root/testout actually was created
            Assertions.assertTrue( files.contains( "testout" ),
                                   "did not manage to create file inside container in the mounted folder." );
        }
        Path methodFolder = verifyMethodNameFolderExistsAndIsUnique( expectedMethodNameFolderRegex );
        Path expectedTempFolder = methodFolder.resolve( expectedFolderName );
        verifyTempFolder( expectedTempFolder, expectedFolderName, methodFolder );
        Assertions.assertEquals( expectedTempFolder, actualTempFolder,
                                 "Temporary folder was not created in the expected location");
        Assertions.assertTrue( expectedTempFolder.resolve( "testout" ).toFile().exists(),
                               "Test file was created in container but not in mounted folder. " +
                               "Probably it was unsuccessfully mounted" );
    }

    @Test
    void createAutomaticallyNamedFolderAndMount() throws Exception
    {
        String expectedMethodNameFolderRegex = this.getClass().getName() + "_createAutomaticallyNamedFolderAndMount_\\d{4}";
        String expectedFolderName = "root";
        Path actualTempFolder;
        try(GenericContainer container = makeContainer())
        {
            actualTempFolder = manager.createFolderAndMountAsVolume( container, "/root" );
            container.start();
            container.execInContainer( "touch", "/root/testout" );
            String files = container.execInContainer( "ls", "/root" ).getStdout();
            // sanity check that /root/testout actually was created
            Assertions.assertTrue( files.contains( "testout" ),
                                   "did not manage to create file inside container in the mounted folder." );
        }
        Path methodFolder = verifyMethodNameFolderExistsAndIsUnique( expectedMethodNameFolderRegex );
        Path expectedTempFolder = methodFolder.resolve( expectedFolderName );
        verifyTempFolder( expectedTempFolder, expectedFolderName, methodFolder );
        Assertions.assertEquals( expectedTempFolder, actualTempFolder,
                                 "Temporary folder was not created in the expected location");
        Assertions.assertTrue( expectedTempFolder.resolve( "testout" ).toFile().exists(),
                               "Test file was created in container but not in mounted folder. " +
                               "Probably it was unsuccessfully mounted" );
    }

    private Path verifyMethodNameFolderExistsAndIsUnique(String expectedMethodNameFolderRegex) throws Exception
    {
        // get methodFolderName from TemporaryFolderManager
        String actualMethodFolderName = manager.methodOutputFolder.getFileName().toString();
        Assertions.assertTrue( Pattern.matches( expectedMethodNameFolderRegex, actualMethodFolderName ),
                               "Folder \"" + manager.methodOutputFolder +
                               "\" does not match expected regex \"" + expectedMethodNameFolderRegex + "\"");

        // verify <METHODNAME> folder was created under the root folder store.
        List<Path> methodNameFolders = Files.list( outFolder )
                                            .filter( path -> path.getFileName()
                                                                 .toString()
                                                                 .matches( expectedMethodNameFolderRegex ) )
                                            .toList();
        Assertions.assertEquals( 1, methodNameFolders.size(), "Expected only one folder called " +
                                                              expectedMethodNameFolderRegex + ". Actual: " +
                                                              methodNameFolders.stream()
                                                                               .map(Path::toString)
                                                                               .collect( Collectors.joining( ",")));
        Path methodFolder = methodNameFolders.get( 0 ); // previous assertion guarantees this to work
        Assertions.assertEquals( methodFolder, manager.methodOutputFolder,
                                 "Folder found in TestTemp is not the same as the one in the folder manager" );
        // make sure the <METHODNAME> folder is marked for cleanup
        Assertions.assertTrue( manager.toCompressAfterAll.contains( methodFolder ),
                               "Did not flag " + methodFolder.getFileName() + " for cleanup. Flagged files are: " +
                               manager.toCompressAfterAll.stream()
                                                         .map(Path::toString)
                                                         .collect( Collectors.joining( ",")));

        return methodFolder;
    }

    private void verifyTempFolder(Path tempFolder, String expectedFolderName, Path expectedParent)
    {
        Assertions.assertTrue( tempFolder.toFile().exists(), "createTempFolder did not create anything" );
        Assertions.assertTrue( tempFolder.toFile().isDirectory(), "Did not create a directory" );
        Assertions.assertEquals(expectedFolderName, tempFolder.toFile().getName(),
                                "Did not give temp directory the expected name" );
        Assertions.assertTrue( tempFolder.getParent().equals( expectedParent ),
                               "Did not create temp folder under expected parent location. Actual: "+tempFolder.getParent() );
    }


    // TEST FOLDER IS CLEANED UP

    private File verifyTarIsCreatedAndUnique(String expectedTarRegex) throws Exception
    {
        // verify outFolder contains ONE tar matching our regex
        List<Path> tarredFiles = Files.list( outFolder )
                                      .filter( path -> path.getFileName()
                                                           .toString()
                                                           .matches( expectedTarRegex ) )
                                      .toList();
        Assertions.assertEquals( 1, tarredFiles.size(), "Expected only one folder called " +
                                                        expectedTarRegex + ". Actual: " +
                                                        tarredFiles.stream()
                                                                   .map(Path::toString)
                                                                   .collect( Collectors.joining( ",")));
        return tarredFiles.get( 0 ).toFile();
    }

    @Test
    void createsTarOfFolder() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING, "Temporary folder zipping disabled" );
        String expectedTarRegex = this.getClass().getName() + "_createsTarOfFolder_\\d{4}\\.tar\\.gz";
        String expectedFileContents = "words words words";

        // create one folder with one file to be zipped.
        Path tempFolder = manager.createFolder( "tozip" );
        Files.writeString( tempFolder.resolve( "testfile" ), expectedFileContents );
        Assertions.assertTrue( tempFolder.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder);

        manager.triggerCleanup();

        File actualTar = verifyTarIsCreatedAndUnique( expectedTarRegex );
        List<String> files = listFilesInTar( actualTar );
        Assertions.assertEquals( 1, files.size(),
                               "Tar file "+actualTar+" exists but is empty." );
        String writtenFile = readFileInTar( actualTar, "/tozip/testfile" );
        Assertions.assertEquals( expectedFileContents, writtenFile );
        // all temporary folder should now be deleted
        Assertions.assertFalse( tempFolder.toFile().exists(), "Temporary folder should have been deleted" );
    }

    @Test
    void createsTarOfFolder_2Files() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING, "Temporary folder zipping disabled" );
        String expectedTarRegex = this.getClass().getName() + "_createsTarOfFolder_2Files_\\d{4}\\.tar\\.gz";
        String expectedFileContents1 = "words1 words1 words1";
        String expectedFileContents2 = "words2 words2 words2";

        // create one folder with one file to be zipped.
        Path tempFolder = manager.createFolder( "tozip" );
        Files.writeString( tempFolder.resolve( "testfile1" ), expectedFileContents1 );
        Assertions.assertTrue( tempFolder.resolve( "testfile1" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder);
        Files.writeString( tempFolder.resolve( "testfile2" ), expectedFileContents2 );
        Assertions.assertTrue( tempFolder.resolve( "testfile2" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder);

        manager.triggerCleanup();

        File actualTar = verifyTarIsCreatedAndUnique( expectedTarRegex );
        List<String> files = listFilesInTar( actualTar );
        Assertions.assertEquals( 2, files.size(),
                                 "Tar file "+actualTar+" exists but is empty." );
        String writtenFile1 = readFileInTar( actualTar, "/tozip/testfile1" );
        String writtenFile2 = readFileInTar( actualTar, "/tozip/testfile2" );
        Assertions.assertEquals( expectedFileContents1, writtenFile1 );
        Assertions.assertEquals( expectedFileContents2, writtenFile2 );
        Assertions.assertFalse( tempFolder.toFile().exists(), "Temporary folder should have been deleted" );
    }

    @Test
    void createsTarOfFolder_2Folders() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING, "Temporary folder zipping disabled" );
        String expectedTarRegex = this.getClass().getName() + "_createsTarOfFolder_2Folders_\\d{4}\\.tar\\.gz";
        String expectedFileContents1 = "words1 words1 words1";
        String expectedFileContents2 = "words2 words2 words2";

        // create one folder with one file to be zipped.
        Path tempFolder1 = manager.createFolder( "tozip1" );
        Files.writeString( tempFolder1.resolve( "testfile" ), expectedFileContents1 );
        Assertions.assertTrue( tempFolder1.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder1);
        Path tempFolder2 = manager.createFolder( "tozip2" );
        Files.writeString( tempFolder2.resolve( "testfile" ), expectedFileContents2 );
        Assertions.assertTrue( tempFolder2.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder2);

        manager.triggerCleanup();

        File actualTar = verifyTarIsCreatedAndUnique( expectedTarRegex );
        List<String> files = listFilesInTar( actualTar );

        Assertions.assertEquals( 2, files.size(),
                                 "Tar file "+actualTar+" exists but does not contain the expected files." );
        String writtenFile1 = readFileInTar( actualTar, "/tozip1/testfile" );
        Assertions.assertEquals( expectedFileContents1, writtenFile1 );
        String writtenFile2 = readFileInTar( actualTar, "/tozip2/testfile" );
        Assertions.assertEquals( expectedFileContents2, writtenFile2 );
        Assertions.assertFalse( tempFolder1.toFile().exists(), "Temporary folder should have been deleted" );
        Assertions.assertFalse( tempFolder2.toFile().exists(), "Temporary folder should have been deleted" );
    }

    @Test
    void createsTarOfFolder_nestedFolders() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING, "Temporary folder zipping disabled" );
        String expectedTarRegex = this.getClass().getName() + "_createsTarOfFolder_nestedFolders_\\d{4}\\.tar\\.gz";
        // creating folders:
        // tempFolder1
        // |  tempFolder2
        // |  | testfile
        String expectedFileContents = "words words words";

        // create one folder with one file to be zipped.
        Path tempFolder1 = manager.createFolder( "tozip1" );
        Path tempFolder2 = manager.createFolder( "tozip2", tempFolder1 );
        Files.writeString( tempFolder2.resolve( "testfile" ), expectedFileContents );
        Assertions.assertTrue( tempFolder2.resolve( "testfile" ).toFile().exists(),
                               "Test failure. Did not successfully write to "+tempFolder2);

        manager.triggerCleanup();
        File actualTar = verifyTarIsCreatedAndUnique( expectedTarRegex );

        List<String> files = listFilesInTar( actualTar );
        Assertions.assertEquals( 1, files.size(),
                               "Tar file "+actualTar+" exists but is empty." );
        String writtenFile = readFileInTar( actualTar,"/tozip1/tozip2/testfile" );
        Assertions.assertEquals( expectedFileContents, writtenFile );
        Assertions.assertFalse( tempFolder1.toFile().exists(), "Temporary folder should have been deleted" );
    }

    // TEST CODE CLEANUP WITH REOWNING

    @Test
    void canSetFolderOwnerTo7474ThenCleanup() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING, "Temporary folder zipping disabled" );
        String expectedTarRegex = this.getClass().getName() + "_canSetFolderOwnerTo7474ThenCleanup_\\d{4}\\.tar\\.gz";
        Path tempFolder = manager.createFolder( "tozip" );
        Files.writeString ( tempFolder.resolve( "testfile" ), "words" );

        manager.setFolderOwnerToNeo4j( tempFolder );
        // verify expected folder owner
        Integer fileUID = (Integer) Files.getAttribute( tempFolder, "unix:uid" );
        Assertions.assertEquals( 7474, fileUID.intValue(),
                                 "Did not successfully set the owner of "+tempFolder );
        // clean up and verify successfully cleaned up
        manager.triggerCleanup();
        verifyTarIsCreatedAndUnique( expectedTarRegex );
        Assertions.assertFalse( tempFolder.toFile().exists(), "Did not successfully delete "+tempFolder );
    }

    @Test
    void canCreateAndCleanupFoldersWithDifferentOwners() throws Exception
    {
        Assumptions.assumeFalse( TestSettings.SKIP_MOUNTED_FOLDER_TARBALLING, "Temporary folder zipping disabled" );
        String expectedTarRegex = this.getClass().getName() + "_canCreateAndCleanupFoldersWithDifferentOwners_\\d{4}\\.tar\\.gz";
        Path tempFolder7474 = manager.createFolder( "tozip7474" );
        Path tempFolderNormal = manager.createFolder( "tozipNormal" );
        Files.writeString( tempFolder7474.resolve( "testfile" ), "words" );
        Files.writeString( tempFolderNormal.resolve( "testfile" ), "words" );

        manager.setFolderOwnerToNeo4j( tempFolder7474 );
        Integer fileUID = (Integer) Files.getAttribute( tempFolder7474, "unix:uid" );
        Assertions.assertEquals( 7474, fileUID.intValue(),
                                 "Did not successfully set the owner of "+tempFolder7474 );

        manager.triggerCleanup();
        verifyTarIsCreatedAndUnique( expectedTarRegex );
        Assertions.assertFalse( tempFolderNormal.toFile().exists(), "Did not successfully delete "+tempFolderNormal );
        Assertions.assertFalse( tempFolder7474.toFile().exists(), "Did not successfully delete "+tempFolder7474 );
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
        internalFilePath = tar.getName().split( "\\.tar\\.gz" )[0] + internalFilePath;
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