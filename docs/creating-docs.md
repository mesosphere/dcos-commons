# Creating Docs for the DC/OS SDK

## Modify the Github Pages Site

Modifications made to the `docs/` directory of the master branch of this repository also appear on the Github pages site: `http://mesosphere.github.io/dcos-commons/`.

## Javadocs Reference

1. From the root directory of this repository, run:

    ```
    javadoc -d docs/ $(find . -name *.java)
    ```

1. Commit your changes to the master branch.

## Swagger API Reference

To modify the Swagger docs:

1. Copy the contents of `swagger-api/sdk-api-swagger-src.yaml` into the online editor at `http://editor.swagger.io/#/`.

1. Make your modifications and make sure no errors appear in the editor.

1. Replace `swagger-api/sdk-api-swagger-src.yaml` with the code you created in the editor.

1. In the editor, go to **Generate Client** > **HTML**.

1. This will download a folder called `html-client` onto your local machine.

1. Open the HTML file in an editor and modify the first lines of the `<body>` section to read:

    ```
    <h1>DC/OS SDK API Reference</h1>
        <div class="app-desc">Version: 0.0.1</div>
        <div class="app-desc">BasePath:/v1</div>
    ```

1. Replace `swagger-api/index.html` with your local file.

1. Commit your changes to the master branch.