### DISCVR Overview

DISCVR is a web-based system designed to help individual labs manage and utilize the data they create. It is a series of modules that extend [LabKey Server](https://labkey.org/), a robust open-source platform designed to manage scientific data with a large and active user base. The objective of DISCVR is to provide additional features tailored specifically to research labs, beyond what is available through a standard LabKey Server.  

This project is closely linked to [DISCVR-Seq](../discvr-seq/overview), which is a sub-project aimed at next-generation sequencing.

### What Does DISCVR Do?
DISCVR makes it easy for individual labs to create and adapt a website to manage their data. It allows labs to leverage the power of a database to store and find information. It builds on many years of experience trying to produce a system that works within the culture and practical constraints of academic labs. DISCVR is designed to add value while understanding that no lab has the time or resources to spend hours cataloging information.

### Is This An Electronic Lab Notebook?
Not really, but it might be useful to think in these terms.  'Electronic Lab Notebook' can have very specific meanings with respect to documentation requirements and if anything this is often extra, redundant work for the lab.  There are features in LabKey Server that one might be able to leverage for this level of documentation; however, it's not what DISCVR attempts to do.  

For our own work, we recognized that there were obvious limits to paper notebooks.  Most experiments and analyses generates files that are not easily captured on paper.  A stack of paper notebooks is not terribly searchable (compare a stack of journals to PubMed).  A very common solution we've seen from labs is to simply have a shared folder where people simply dump files as they're created.  Maybe there is some organizational scheme to the folders, but our experience is that these quickly break down and become unwieldy.  

### Then What Is It?
The core idea behind DISCVR is the concept of Workbooks.  For simplicity, a workbook might be roughly equal to an experiment that is entered into your notebook, or perhaps an analysis of data.  In LabKey, these are just a lightweight way to group data.  DISCVR adds this layer above most existing mechanisms within LabKey to store/capture data.  For example, most of the time when you hit 'import data' in DISCVR, the first question you see is: 'Do you want to create a new workbook, or add to an existing one?'.  The workbooks are numbered, and this number is an easy way to identify it, which can be written into your notebook or on vials in the freezer. 

As far as actually storing data, the simplest way is to simply use DISCVR to deposit files into the workbook(s).  This is not an enormous benefit over a shared folder, but it is centralized, searchable and web-accessible.  It also does not add much extra burden to lab members (i.e. no one is filling out lots of extra forms).   

There way be certain types of data your group wishes to capture with more structure.  These could include results of assays you perform repeatedly (ELISAs, chem panels, etc.), records of when experiments occurred (dates of MRIs or other procedures), freezer inventories, or anything else.  These might currently exist in some kind of centralized excel file. DISCVR uses any of LabKey's existing capabilities to shift data from excel into a web-accessible database.  Generally speaking, it is possible to define custom tables and then use simple excel templates to upload data.  DISCVR includes some common functions including a freezer/inventory table, and DNA Oligos.  However, there is a lot of diversity in needs across labs, and it is possible to create custom assays/tables.  [Lists](https://www.labkey.org/Documentation/wiki-page.view?name=advancedListTutorial) are the simplest mechanism in LabKey, though other mechanisms exist. 

### How Do I Get Started?
[Please read these instructions to begin](https://github.com/bbimber/discvr/wiki/Getting-Started)

### How Does DISCVR Relate to LabKey Server?
LabKey Server is an open-source platform designed to manage scientific data. It is used by a large and diverse set of research groups, both large and small. The core of LabKey is built by professional software developers, and has enterprise level security and data-sharing capabilities. DISCVR consists of modules that extend LabKey. Our goal is to leverage the features of LabKey Server, and to professionally augment them where appropriate. New versions of DISCVR are released in conjunction with LabKey Server releases.  DISCVR is open-source, and has regular automated software testing.

### About The Author
Ben Bimber received his Ph.D. from the University of Wisconsin where he studied the genetics of host/viral interactions. The ideas in DISCVR borrow heavily from older systems originally created for the Watkins and O'Connor Laboratories. Since graduation, Ben worked for LabKey Software as a software developer, and is currently a Research Assistant Professor at Oregon Health & Science University.