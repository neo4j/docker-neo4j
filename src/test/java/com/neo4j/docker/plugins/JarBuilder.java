package com.neo4j.docker.plugins;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Utility to create jar files containing classes from the current classpath.
 */
public class JarBuilder
{
    public URL createJarFor( File f, Class<?>... classesToInclude ) throws IOException
    {
        try ( FileOutputStream fout = new FileOutputStream( f ); JarOutputStream jarOut = new JarOutputStream( fout ) )
        {
            for ( Class<?> target : classesToInclude )
            {
                String fileName = target.getName().replace( ".", "/" ) + ".class";
                jarOut.putNextEntry( new ZipEntry( fileName ) );
                jarOut.write( classCompiledBytes( fileName ) );
                jarOut.closeEntry();
            }
        }
        return f.toURI().toURL();
    }

    private byte[] classCompiledBytes( String fileName ) throws IOException
    {
        try ( InputStream in = getClass().getClassLoader().getResourceAsStream( fileName ) )
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while ( in.available() > 0 )
            {
                out.write( in.read() );
            }

            return out.toByteArray();
        }
    }
}
