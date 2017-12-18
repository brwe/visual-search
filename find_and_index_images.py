import os

# We mounted the parameter from the bash script to /images
rootDir = '/images'
for dirName, subdirList, fileList in os.walk(rootDir):
    print('Found directory: %s' % dirName)
    for fname in fileList:
        print('\t%s' % fname)
        # here we need to generate the api call if we found an image