// This file is property of Recursive Loop Ltd.
//
// Author: Rob Jinman
// Web: http://recursiveloop.org
// Copyright Recursive Loop Ltd 2015
// Copyright Rob Jinman 2015


package com.recursiveloop.integrationtest;

import com.recursiveloop.integrationtest.common.TestSuite;

import com.recursiveloop.resources.RcAuthToken;
import com.recursiveloop.models.UserCredentials;
import com.recursiveloop.models.AuthToken;
import com.recursiveloop.exceptions.InternalServerException;
import com.recursiveloop.exceptions.UnauthorisedException;
import com.recursiveloop.DataConnection;
import com.recursiveloop.annotations.Config;

import javax.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.extension.rest.client.ArquillianResteasyResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;
import java.net.URL;
import javax.ws.rs.core.Response;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.WebApplicationException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.Types;
import java.sql.CallableStatement;
import java.util.UUID;


@RunWith(Arquillian.class)
public class RcAuthTokenTest {

  @Deployment
  public static WebArchive createDeployment() {
    return ShrinkWrap.create(WebArchive.class, "RcAuthTokenTest.war")
      .addPackage("com/recursiveloop/integrationtest/common")
      .addPackage("com/recursiveloop")
      .addPackage("com/recursiveloop/models")
      .addPackage("com/recursiveloop/resources")
      .addPackage("com/recursiveloop/annotations")
      .addPackage("com/recursiveloop/exceptions")
      .addAsResource("config.properties")
      .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
      .addAsWebInfResource("jboss-web.xml", "jboss-web.xml")
      .setWebXML("web.xml");
  }

  @Inject
  TestSuite m_testSuite;

  @Inject
  DataConnection m_data;

  @Test
  @InSequence(1)
  public void connection_ok() {
    System.out.println("**((CONNECTION_OK))**");

    Assert.assertTrue(m_data.isGood());
  }

  private static String m_email = "martha123@website.com";
  private static String m_username = "martha";
  private static String m_password = "pineapple";

  @Test
  @InSequence(2)
  public void setup() throws SQLException {
    System.out.println("**((SETUP))**");
    m_testSuite.prepDB();

    String q = "{ ? = call rl.registeruser(?) }";
    CallableStatement cs = m_data.getConnection().prepareCall(q);

    cs.registerOutParameter (1, Types.VARCHAR);
    cs.setString(2, m_email);
    cs.execute();

    String code = cs.getString(1);

    q = "{ ? = call rl.confirmUser(?, ?, ?) }";
    cs = m_data.getConnection().prepareCall(q);

    cs.registerOutParameter (1, Types.OTHER);
    cs.setString(2, m_username);
    cs.setString(3, m_password);
    cs.setString(4, code);
    cs.execute();

    UUID accountId = (UUID)cs.getObject(1);

    m_data.getConnection().commit();
  }

  @Test
  @InSequence(3)
  @RunAsClient
  @Consumes(MediaType.APPLICATION_JSON)
  public void with_good_credentials(@ArquillianResteasyResource("rest") RcAuthToken rcAuthToken)
    throws InternalServerException, UnauthorisedException {

    System.out.println("**((WITH_GOOD_CREDENTIALS))**");

    UserCredentials credentials = new UserCredentials();
    credentials.setUsername("martha");
    credentials.setPassword("pineapple");

    AuthToken token = rcAuthToken.doPost(credentials);

    Assert.assertNotNull(token);
    Assert.assertEquals("F3AFAB0893DC740D84128C4DD0F012B9", token.getToken());
  }

  @Test
  @InSequence(4)
  @RunAsClient
  @Consumes(MediaType.APPLICATION_JSON)
  public void with_bad_credentials(@ArquillianResteasyResource("rest") RcAuthToken rcAuthToken) {
    System.out.println("**((WITH_BAD_CREDENTIALS))**");

    UserCredentials credentials = new UserCredentials();
    credentials.setUsername("martha");
    credentials.setPassword("pinapple");

    try {
      rcAuthToken.doPost(credentials);
    }
    catch (WebApplicationException ex) {
      Response r = ex.getResponse();
      Assert.assertEquals(401, r.getStatus());
      return;
    }
    catch (Exception ex) {}

    Assert.assertTrue(false);
  }

  @Test
  @InSequence(5)
  public void tear_down() throws SQLException {
    System.out.println("**((TEAR_DOWN))**");
    m_testSuite.prepDB();
  }
}
