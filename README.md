<body>
    <header>
        <div class="container">
            <h1>Поисковой движок "Helion"</h1>
            <h2><b>"Helion"</b> - search engine</h2>
            <div class="image-header">
                <img src="data/image/helion2.png" alt="Helion">
            </div>
            <div class="header-text"> <h3><b>"Helion"</b> - Web-приложение позволяющее производить поиск информации по сайтам.</h3>  </div>
        </div>
    </header>
    <main>
        <div class="container">
            <h2>Что делает "движок"?</h2>
            <div class="list-work-engine">
                <li>Производит индексацию сайтов, заданных в конфигурационным файле;</li>
                <li>По итогам индексации сайта осуществляет предоставление информации по поисковым запросам пользователей;</li>
                <li>Предоставляет информацию о статусе индексации, с количеством проиндексированных сайтов и странниц на нем, и количестве лемм на сайте;</li> 
                <li>Позволяет произвести переиндексацию сайта в целом, а так же отдельных страниц сайта;</li>
                <li>Позволяет оставить запущенную индексацию;</li>
                <li>Позволяет производить поиск по выбранному сайту</li>
            </div>
            <h2>Интерфейс приложения</h2>
            <div>
                <div>
                    <img src="./data/image/one.png" alt="">
                </div>
                <h3>Стартовая страница приложения и информациооное меню по статусу индексации сайтов</h3>
                <div>
                    <img src="./data/image/two.png" alt="">
                </div>
                <h3>Меню запуска индексации и переиндексации сайтов/страниц</h3>
                <div>
                    <img src="./data/image/tree.png" alt="">
                </div>
                <h3>Поиковое меню приложения</h3>
            </div>
            <h2>Для работы приложения потребуется</h2>
            <div>
                <li>База данных MySQL/PostgreSQL</li>
                <li>Настроить конфигурационный файл для подключения БД</li>
            </div>
        </div>
    </main>
</body>

<style>
body{
    margin: 0;
    padding: 0;
    background-color: black;
    color: white;
}

.container{
    max-width: 1100px;
    text-align: center;
    margin: auto;

}
.image-header{
    margin: auto;
    max-width: max-content;
    background-color: black;
    color: rgb(50, 83, 96);
    box-shadow: 0px -100px 3000px 1px;

}

.list-work-engine{
    text-align: left;
    padding-left: 30px;
}
</style>