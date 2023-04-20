
package com.uploadfile.vishal.upload_file.Service

import com.uploadfile.vishal.upload_file.Exception.UnsupportedFormat
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import reactor.core.publisher.Mono
import org.springframework.http.*
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux

import java.io.FileNotFoundException
import java.lang.IndexOutOfBoundsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import javax.naming.SizeLimitExceededException
enum class FileFormat {
    jpeg, pdf, csv, xls, doc, png, jpg
}
@Service
class FileUploadDownloadService {
    private val fileStorage = Paths.get("./upload").toAbsolutePath().normalize()
    var maxContentLength = 5000000L
    fun uploadFile(filePartMono: Flux<FilePart>, contentLength: Long): Mono<ResponseEntity<String>> {
        //can be read and changed from multiple threads
        var count = AtomicLong() //LongAdder()
        println(maxContentLength)
        println(contentLength)
        return filePartMono
            .flatMap { fp:FilePart ->
                when {
                    count.incrementAndGet() > 1 -> Flux.error(IndexOutOfBoundsException())
                    else -> Flux.just(fp)
                }
            }
//        return filePartMono
//            .flatMap { fp: FilePart ->
//                count
//                    .doOnNext { it.increment() }
//                    .filter { it.sum() <= 1 }
//                    .flatMapMany { Flux.just(fp) }
//                    .switchIfEmpty(Flux.error(IndexOutOfBoundsException()))
//            }
            .single()               //emits single value exception on error
            .flatMap { fp: FilePart ->
                val supportedFormats = enumValues<FileFormat>().map { it.name}
                when {
                    (contentLength > maxContentLength) -> {
                        maxContentLength = contentLength
                        Mono.error(SizeLimitExceededException())
                    }
                    fp.filename().isEmpty() -> Mono.error(FileNotFoundException())
                    //contentLength <= 174L -> Mono.error(FileNotFoundException())  //todo
                    fp.filename().substring(fp.filename().lastIndexOf('.') + 1) !in supportedFormats -> Mono.error(
                        UnsupportedFormat()
                    )
                    else -> {
                        //to write the file to the destination path
                        fp.transferTo(fileStorage.resolve(fp.filename()))
                            .then(Mono.just(ResponseEntity.ok().body("/download/${fp.filename()}")))
                    }
                }
            }
    }


    fun downloadFile( filename: String): Mono<ResponseEntity<Resource>> {
        //get path using filestorage
        val filePath: Path = fileStorage.toAbsolutePath().normalize().resolve(filename)
        return Mono.fromCallable {
            when {
                //check if file exist if not return file not found exception
                !Files.exists(filePath) -> throw FileNotFoundException()
                else -> {
                    //create a resource object from file using url resource class, represent resource loaded from a url
                    val resource: Resource = UrlResource(filePath.toUri())
                    //creates http headers used to specify http header for the response
                    val httpHeaders = HttpHeaders()
                    //the fxn sets filename header to a value of filename
                    httpHeaders.add("File-Name", filename)
                    //content disposition as attachment
                    //filename =<filename> tells the browser to treat the response as an attachment
                    httpHeaders.add(CONTENT_DISPOSITION, "attachment;File-Name=" + resource.filename)
                    //it then prompts the user to download the file with the specified filename
                    ResponseEntity.ok().contentType(MediaType.parseMediaType(Files.probeContentType(filePath)))
                        .headers(httpHeaders).body(resource)
                }
            }
        }
    }
}