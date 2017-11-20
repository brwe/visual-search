# Description

This is totally work in progress so move along, nothing to see.


# Service apis

To store and image call

```
POST /image
{
    "imageUrl": "... for example http://www.drstuspodcast.com/wp-content/uploads/2017/05/Move-Along-2.jpg"
}

```

This will
 - fetch the image
 - extract some features
 - store them in elasticsearch together with some metadata
 
 
Currently:
```
POST /image
{
    "imageUrl": "http://www.drstuspodcast.com/wp-content/uploads/2017/05/Move-Along-2.jpg"
}


```
will store the following document in elasticsearch:
````
{
    "receivedBytes": 9423,
    "imageUrl": "http://www.drstuspodcast.com/wp-content/uploads/2017/05/Move-Along-2.jpg"
}
````

and return its elasticsearch id:
````
{
    "_id": "AV_F2mX47zsWila-6evq"
}
````
# Run locally

The local setup runs in docker containers.

To start the image service only, run

```
$docker-compose up visual-search
```

If you like to index some test images from [open images project](https://github.com/openimages/dataset)
then run

```
$./setup.sh
$docker-compose up index-open-images-test-set
```



# Build

Build with 

```
./gradlew clean bootJar
```

The service needs elasticsearch to store results.
Run the service, set the variables `ELASTIC_HOST` and `ELASTIC_PORT` and run:

```
java -Xmx1g -Xms1g -jar -DELASTIC_HOST=$ELASTIC_HOST -DELASTIC_PORT=$ELASTIC_PORT ./build/libs/visual-search.jar
```

Run tests with
````
./gradlew test
````

Build idea:
````
./gradlew idea
````

