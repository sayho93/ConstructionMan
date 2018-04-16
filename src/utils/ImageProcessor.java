package utils;

import configs.Constants;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

public class ImageProcessor {

    private Set<Integer> sizes;
    private Set<String> validExtensions;

    public ImageProcessor(Set<Integer> sizes, Set<String> exts){
        this.sizes = sizes;
        this.validExtensions = exts;
    }

    public void makeSizeDirectory() throws IOException{
        final Iterator<Integer> iter = this.sizes.iterator();
        while(iter.hasNext()){
            int size = iter.next();
            new File(Constants.FILE_CONST.UPLOAD_DIR + "/" + size).mkdir();
        }
        new File(Constants.FILE_CONST.UPLOAD_DIR + "/" + Constants.FILE_CONST.ORIGINAL_DIR).mkdir();
    }

    public boolean isValidExtension(String fileName){
        final Iterator<String> iter = this.validExtensions.iterator();
        while(iter.hasNext()){
            final String ext = iter.next();
            if(fileName.endsWith(ext)) return true;
        }
        return false;
    }

    public void resizeAndSave(String originPath){
        // TODO
    }

}
