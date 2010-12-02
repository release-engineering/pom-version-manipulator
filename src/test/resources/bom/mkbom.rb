#!/usr/bin/env ruby

require 'optparse'
require 'rexml/document'
require 'pp'

include REXML

class RepoBuilder
  
  def initialize()
    @options = {
      :group_id => 'group',
      :artifact_id => 'bom',
      :version => '1',
      :name => 'Bill of Materials',
      :output => 'pom.xml',
      :qualifier => 'redhat-1',
    }
    
    @run = true
    
    ARGV.options do |opts|
      opts.banner =<<-EOB

"Usage:  #{File.basename($PROGRAM_NAME)} [OPTIONS] <repository-path>[ <repository-path>]*"

      EOB
      
      opts.on( '-g', '--groupId=GROUP_ID', 'The groupId to use for the BOM coordinate'){|gid| @options[:group_id]=gid}
      opts.on( '-a', '--artifactId=ARTIFACT_ID', 'The artifactId to use for the BOM coordinate'){|aid| @options[:artifact_id]=aid}
      opts.on( '-v', '--version=VERSION', 'The version to use for the BOM coordinate'){|ver| @options[:version]=ver}
      opts.on( '-n', '--name=NAME', 'Value of the BOM <name/> element'){|name| @options[:name]=name}
      opts.on( '-o', '--output=FILE', 'POM file to write'){|file| @options[:output]=file}
      opts.on( '-q', '--qualifier=QUALIFIER', "Append to all versions in the dependency list \n\t\t\t\t\t(-q 'build-1' results in '<version>-build-1')"){|qual| @options[:qualifier]=qual}
      
      opts.separator "\n"
      
      opts.on_tail( "-h", "--help",
               "Show this message." ) do
        puts opts
        puts ''
        @run = false
      end
      
      begin
        @repo_dirs = opts.parse!
      rescue Exception => e 
        puts "#{e}\n#{e.backtrace.join("\n")}"
        exit
      end
      
      if ( @run && @repo_dirs.length < 1 )
        puts "You must supply at least one repository directory."
        @run = false
      end
    end
  end #initialize
  
  def build
    exit 0 unless @run
    
    puts ''
    puts "Using configuration:"
    pp @options
    puts ''
    puts "Processing POMs in directories:"
    @repo_dirs.each {|dir| puts "- #{dir}"}
    puts ''
    
    deps = Array.new
    @repo_dirs.each do |dir|
      Dir.glob( File.join( dir, '**/*.pom' ) ) do |file|
        pom = Document.new(File.read(file))
        
        g = XPath.first( pom, 'project/groupId' )
        g = XPath.first( pom, 'project/parent/groupId' ) unless g
        
        gid = g.text
        
        a = XPath.first( pom, 'project/artifactId' )
        
        aid = a.text
        
        v = XPath.first( pom, 'project/version' )
        v = XPath.first( pom, 'project/parent/version' ) unless v
        
        ver = v.text
        ver << '-' << @options[:qualifier] if @options[:qualifier]
        
        e = Element.new('dependency')
        e.add_element( 'groupId' ).text=gid
        e.add_element( 'artifactId' ).text=aid
        e.add_element( 'version' ).text=ver
        
        deps << e
      end
    end
    
    # <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    #   xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    
    p = Element.new('project')
    p.add_namespace('http://maven.apache.org/POM/4.0.0')
    p.add_namespace('xsi', 'http://www.w3.org/2001/XMLSchema-instance')
    p.add_attribute('xsi:schemaLocation', 'http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd')
    
    p.add_element('modelVersion').text = '4.0.0'
    p.add_text( "\n\n  " )
    
    p.add_element('groupId').text = @options[:group_id]
    p.add_element('artifactId').text = @options[:artifact_id]
    p.add_element('version').text = @options[:version]
    p.add_text( "\n\n  " )
    
    if ( @options[:name] )
      p.add_element('name').text = @options[:name]
      p.add_text( "\n\n  " )
    end
    
    ds = p.add_element('dependencyManagement').add_element('dependencies')
    deps.each {|dep| ds << dep}
    
    pom = Document.new( nil, {:respect_whitespace => :all})
    pom << XMLDecl.new
    pom.add_element( p )
    
    if ( @options[:output] )
      File.open(@options[:output], 'w+') do |f|
        pom.write( f, 0 )
        f.puts ''
      end
      
      puts "BOM (#{@options[:group_id]}:#{@options[:artifact_id]}:#{@options[:version]}) written to: #{@options[:output]}"
    else
      pom.write( $stdout, 0 )
      puts ''
    end
  end #build
  
end #class

RepoBuilder.new().build
