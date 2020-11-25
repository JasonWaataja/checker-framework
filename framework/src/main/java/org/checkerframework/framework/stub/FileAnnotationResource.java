package org.checkerframework.framework.stub;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** {@link File}-based implementation of {@link AnnotationFileResource}. */
public class FileAnnotationResource implements AnnotationFileResource {
    /** File for the resource. */
    private final File file;

    /**
     * Constructs a {@code AnnotationFileResource} for the specified stub file.
     *
     * @param file the stub file
     */
    public FileAnnotationResource(File file) {
        this.file = file;
    }

    @Override
    public String getDescription() {
        return file.getAbsolutePath();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }
}
