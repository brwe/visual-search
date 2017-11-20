# Description

This is totally work in progress so move along, nothing to see.


# Service apis

## Index image features

To store an image call

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

## Search similar images

To find similar images call:

```
POST /image_search
{
    "imageUrl": "... for example http://www.drstuspodcast.com/wp-content/uploads/2017/05/Move-Along-2.jpg"
}

```

This will
 - fetch the image
 - extract some features
 - generate an elasticsearch query from the features
 - query elastic and return the resulting search response
 
 Response:
```
{
    "took": 41,
    "timed_out": false,
    "_shards": {
        "total": 5,
        "successful": 5,
        "skipped": 0,
        "failed": 0
    },
    "hits": {
        "total": 2310,
        "max_score": 1,
        "hits": [
            {
                "_index": "images",
                "_type": "processed_images",
                "_id": "AV_ZhsRlvagrm6JfuOwr",
                "_score": 1,
                "_source": {
                    "receivedBytes": 113344,
                    "imageUrl": "https://farm8.staticflickr.com/5005/5356517508_b629917c6c_o.jpg"
                }
            },
            ...
```

Currently the query is just a function score query that ranks images by how many bytes they contain, the closer to the query image the better:

```
{
  "query": {
    "function_score": {
      "functions": [
        {
          "gauss": {
            "receivedBytes": {
              "origin": 113344,
              "scale": 10000
            }
          }
        }
      ]
    }
  }
}
```

# Run locally

The local setup runs in docker containers.

To build the containers run:

```
docker-compose build

```

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

